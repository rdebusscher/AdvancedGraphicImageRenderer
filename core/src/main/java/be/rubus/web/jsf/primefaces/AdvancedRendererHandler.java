/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package be.rubus.web.jsf.primefaces;

import javax.faces.component.UIComponent;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagConfig;
import javax.faces.view.facelets.TagHandler;
import java.io.IOException;

/**
 */
public class AdvancedRendererHandler extends TagHandler {

    public static final String ADVANCED_RENDERING = "ADVANCED_RENDERING";

    private Boolean needAdvancedRendering;

    public AdvancedRendererHandler(TagConfig config) {
        super(config);
        TagAttribute advancedRendering = config.getTag().getAttributes().get("value");
        needAdvancedRendering = Boolean.valueOf(advancedRendering.getValue());
    }

    @Override
    public void apply(FaceletContext ctx, UIComponent parent) throws IOException {
        parent.getAttributes().put(ADVANCED_RENDERING, needAdvancedRendering);
    }
}
