/*
* ============LICENSE_START=======================================================
*  Copyright (C) 2020 Nordix Foundation. All rights reserved.
*  ================================================================================
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*        http://www.apache.org/licenses/LICENSE-2.0
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*
*  SPDX-License-Identifier: Apache-2.0
*  ============LICENSE_END=========================================================
*/

import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { UiElementsModule } from 'app/ng2/components/ui/ui-elements.module';
import { TranslateModule } from 'app/ng2/shared/translator/translate.module';
import { SubstitutionFilterComponent } from "./substitution-filter.component";
import {AccordionModule} from "onap-ui-angular/dist/accordion/accordion.module";

@NgModule({
    declarations: [
        SubstitutionFilterComponent
    ],
    imports: [
        CommonModule,
        UiElementsModule,
        TranslateModule,
        AccordionModule
    ],
    exports: [
        SubstitutionFilterComponent
    ],
    entryComponents: [
        SubstitutionFilterComponent
    ],
    providers: []
})
export class SubstitutionFilterModule {
}
