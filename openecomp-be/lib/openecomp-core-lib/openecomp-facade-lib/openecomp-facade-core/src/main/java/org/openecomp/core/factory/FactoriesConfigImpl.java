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

package org.openecomp.core.factory;


import org.openecomp.core.factory.api.FactoriesConfiguration;
import org.openecomp.core.utilities.file.FileUtils;
import org.openecomp.core.utilities.json.JsonUtil;
import org.openecomp.sdc.common.errors.SdcConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FactoriesConfigImpl implements FactoriesConfiguration {


  private static final String FACTORY_CONFIG_FILE_NAME = "factoryConfiguration.json";
  private static final Map FACTORY_MAP = new HashMap();
  private static boolean initialized = false;

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getFactoriesMap() {
    synchronized (this) {
      if (!initialized) {
        init();
        initialized = true;
      }
    }
    return FACTORY_MAP;
  }

  private void init() {

    List<URL> factoryConfigUrlList = FileUtils.getAllLocations(FACTORY_CONFIG_FILE_NAME);
    for (URL factoryConfigUrl : factoryConfigUrlList) {

      try (InputStream stream = factoryConfigUrl.openStream()) {
        FACTORY_MAP.putAll(JsonUtil.json2Object(stream, Map.class));
      } catch (IOException e) {
        throw new SdcConfigurationException("Failed to initialize Factory from '" + factoryConfigUrl.getPath() +"'", e);
      }
    }
  }
}

