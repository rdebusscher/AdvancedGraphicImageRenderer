/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package be.rubus.web.jsf.primefaces;

import java.io.InputStream;
import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.faces.context.FacesContext;

/**
 */
public class GraphicImageUtil {

    public static GraphicImageManager retrieveManager(FacesContext context) {
        ELContext eLContext = context.getELContext();
        ValueExpression ve = context
                .getApplication()
                .getExpressionFactory()
                .createValueExpression(context.getELContext(), buildElForGraphicImageManager(),
                        GraphicImageManager.class);
        return (GraphicImageManager) ve.getValue(eLContext);

    }

    private static String buildElForGraphicImageManager() {
        StringBuilder result = new StringBuilder();
        result.append("#{").append(GraphicImageManager.class.getSimpleName()).append("}");
        return result.toString();

    }

    /**
     * Determines if a given input stream has bytes available to be read.
     *
     * <P>
     * Bytes availableThis normally happen during the first time a StreamContent is being rendered, or, otherwise, when
     * a new image gets uploaded. On the scenario where, for example, the page is refreshed, the StreamContent will be
     * closed and in such a scenario we would want to continue using the old tmp file image.
     */
    public static boolean isInputStreamAvailable(InputStream inputStream) {
        try {
            return inputStream.available() > 0;
        } catch (Exception ignoreE) {
            // The GraphicImageManager has closed the input stream for the tmp file
            return false;
        }
    }
}
