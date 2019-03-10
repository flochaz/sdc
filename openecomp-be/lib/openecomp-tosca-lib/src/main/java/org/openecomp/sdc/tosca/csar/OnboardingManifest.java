/*
 * Copyright © 2016-2017 European Support Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openecomp.sdc.tosca.csar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.openecomp.sdc.common.errors.Messages;
import org.openecomp.sdc.logging.api.Logger;
import org.openecomp.sdc.logging.api.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.openecomp.core.validation.errors.ErrorMessagesFormatBuilder.getErrorWithParameters;
import static org.openecomp.sdc.tosca.csar.CSARConstants.METADATA_MF_ATTRIBUTE;
import static org.openecomp.sdc.tosca.csar.CSARConstants.NON_MANO_MF_ATTRIBUTE;
import static org.openecomp.sdc.tosca.csar.CSARConstants.SEPERATOR_MF_ATTRIBUTE;
import static org.openecomp.sdc.tosca.csar.CSARConstants.SOURCE_MF_ATTRIBUTE;

public class OnboardingManifest implements Manifest{
    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingManifest.class);
    private Map<String, String> metadata;
    private List<String> sources;
    private List<String> errors;
    private Map<String, List<String>> nonManoSources;

    private OnboardingManifest() {
        errors = new ArrayList<>();
        sources = new ArrayList<>();
        metadata = new HashMap<>();
        nonManoSources = new HashMap<>();
    }

    /**
     * This Method will parse manifest, extracting fields mandatory/non-mandatory,
     * if error occurred it's recorded and will be used for deciding if manifest is valid
     * @param is manifest file input stream
     * @return Manifest object
     */
    public static Manifest parse(InputStream is) {
        OnboardingManifest manifest = new OnboardingManifest();
        try {
            ImmutableList<String> lines = manifest.readAllLines(is);
            manifest.processManifest(lines);
        } catch (IOException e){
            LOGGER.error(e.getMessage(),e);
            manifest.errors.add(Messages.MANIFEST_PARSER_INTERNAL.getErrorMessage());
        }
        return manifest;
    }

    private void processManifest(ImmutableList<String> lines) {
        if(lines == null || lines.isEmpty()){
            errors.add(Messages.MANIFEST_EMPTY.getErrorMessage());
            return;
        }
        Iterator<String> iterator = lines.iterator();
        //SOL004 #4.3.2: The manifest file shall start with the package metadata
        String line = iterator.next();
        if(!line.trim().equals(METADATA_MF_ATTRIBUTE + SEPERATOR_MF_ATTRIBUTE)){
            reportError(line);
            return;
        }
        //handle metadata
        processMetadata(iterator);

        if (errors.isEmpty()) {
            if (metadata.isEmpty()) {
                errors.add(Messages.MANIFEST_NO_METADATA.getErrorMessage());
            }
            if (sources.isEmpty()) {
                errors.add(Messages.MANIFEST_NO_SOURCES.getErrorMessage());
            }
        }
    }

    private void processSourcesAndNonManoSources(Iterator<String> iterator, String prevLine) {
        if(prevLine.isEmpty()){
            if(iterator.hasNext()){
                processSourcesAndNonManoSources(iterator, iterator.next());
            }
        }else if(prevLine.startsWith(SOURCE_MF_ATTRIBUTE+SEPERATOR_MF_ATTRIBUTE)){
            processSource(iterator, prevLine);
        } else if(prevLine.startsWith(NON_MANO_MF_ATTRIBUTE+SEPERATOR_MF_ATTRIBUTE)){
            //non mano should be the last bit in manifest file,
            // all sources after non mano will be placed to the last non mano
            // key, if any other structure met error reported
            processNonManoInputs(iterator, iterator.next());
        }else{
            reportError(prevLine);
        }
    }

    private void processSource(Iterator<String> iterator, String prevLine) {
        String value = prevLine.substring((SOURCE_MF_ATTRIBUTE + SEPERATOR_MF_ATTRIBUTE).length()).trim();
        sources.add(value);
        if(iterator.hasNext()) {
            processSourcesAndNonManoSources(iterator, iterator.next());
        }
    }

    private void processMetadata(Iterator<String> iterator) {
        if(!iterator.hasNext()){
            return;
        }
       String line = iterator.next();
       if(line.isEmpty()){
           processMetadata(iterator);
           return;
       }
       String[] metaSplit = line.split(SEPERATOR_MF_ATTRIBUTE);
        if (metaSplit.length < 2){
            reportError(line);
            return;
        }
        if (!metaSplit[0].equals(SOURCE_MF_ATTRIBUTE) && !metaSplit[0].equals(NON_MANO_MF_ATTRIBUTE)){
            String value = line.substring((metaSplit[0] + SEPERATOR_MF_ATTRIBUTE).length()).trim();
            metadata.put(metaSplit[0].trim(),value.trim());
            processMetadata(iterator);
        }
        else {
            processSourcesAndNonManoSources(iterator, line);
        }
    }

    private void processNonManoInputs(Iterator<String> iterator, String prevLine) {
        //Non Mano input should always start with key, if no key available report an error
        if(prevLine.trim().equals(SOURCE_MF_ATTRIBUTE + SEPERATOR_MF_ATTRIBUTE)){
            reportError(prevLine);
            return;
        }
        //key should contain : separator
        if(!prevLine.contains(SEPERATOR_MF_ATTRIBUTE)){
            reportError(prevLine);
            return;
        }
        //key shouldn't have value in the same line
        String[] metaSplit = prevLine.trim().split(SEPERATOR_MF_ATTRIBUTE);
        if (metaSplit.length > 1){
            reportError(prevLine);
            return;
        }
        int index = prevLine.indexOf(':');
        if(index > 0){
            prevLine = prevLine.substring(0, index);
        }
        processNonManoSource(iterator, prevLine, new ArrayList<>());

    }

    private void processNonManoSource(Iterator<String> iterator, String key, List<String> sources) {
        if(!iterator.hasNext()){
            return;
        }
        String line = iterator.next();
        if(line.isEmpty()){
            processNonManoSource(iterator, key, sources);
        }else if(line.trim().startsWith(SOURCE_MF_ATTRIBUTE + SEPERATOR_MF_ATTRIBUTE)){
            String value = line.replace(SOURCE_MF_ATTRIBUTE + SEPERATOR_MF_ATTRIBUTE, "").trim();
            sources.add(value);
            processNonManoSource(iterator, key, sources);
        }else {
            processNonManoInputs(iterator, line);
        }
        nonManoSources.put(key.trim(), sources);
    }

    private void reportError(String line) {
        errors.add(getErrorWithParameters(Messages.MANIFEST_INVALID_LINE.getErrorMessage(), line));
    }

    private ImmutableList<String> readAllLines(InputStream is) throws IOException {
        if(is == null){
            throw new IOException("Input Stream cannot be null!");
        }
        ImmutableList.Builder<String> builder = ImmutableList.<String> builder();
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8.newDecoder()))) {
            bufferedReader.lines().forEach(builder::add);
        }
        return builder.build();
    }

    public Map<String, String> getMetadata() {
        if (!isValid()){
            return Collections.emptyMap();
        }
        return ImmutableMap.copyOf(metadata);
    }

    public List<String> getSources() {
        if (!isValid()){
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(sources);
    }

    public List<String> getErrors() {
        return  ImmutableList.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public Map<String, List<String>> getNonManoSources() {
        if (!isValid()){
            return Collections.emptyMap();
        }
        return ImmutableMap.copyOf(nonManoSources);
    }
}
