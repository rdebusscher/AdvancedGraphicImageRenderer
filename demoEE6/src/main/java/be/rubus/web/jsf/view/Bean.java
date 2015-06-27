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

import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;
import javax.faces.bean.SessionScoped;
import javax.faces.bean.ViewScoped;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ManagedBean(name = "imageBean")
@ViewScoped
public class Bean  implements Serializable{
	private StreamedContent image;
	private List<Data> dataList;

	public Bean() {
		InputStream stream = this.getClass().getResourceAsStream("/primefaces.png");
		image = new DefaultStreamedContent(stream, "image/png");

		dataList = new ArrayList<Data>();
		dataList.add(new Data(1));
		dataList.add(new Data(2));
	}

	public StreamedContent getImage() {
		return this.image;
	}

	public List<Data> getDataList() {
		return dataList;
	}
}
