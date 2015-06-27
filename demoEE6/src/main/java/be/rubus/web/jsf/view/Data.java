/**
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied.  See the License for the
  * specific language governing permissions and limitations
  * under the License.
 */
package be.rubus.web.jsf.view;

import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import java.io.InputStream;

/**
 */
public class Data {

	private int code;
	private StreamedContent image;

	public Data(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public StreamedContent getImage() {
		if (image == null) {
			image = loadImage();
		}
		return image;
	}

	private StreamedContent loadImage() {

		InputStream stream = this.getClass().getResourceAsStream("/primefaces_"+code+".png");
		return new DefaultStreamedContent(stream, "image/png");

	}
}
