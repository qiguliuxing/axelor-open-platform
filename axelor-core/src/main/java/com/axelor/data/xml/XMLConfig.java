/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.data.xml;

import com.axelor.data.XStreamUtils;
import com.axelor.data.adapter.DataAdapter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@XStreamAlias("xml-inputs")
public class XMLConfig {

  @XStreamImplicit(itemFieldName = "adapter")
  private List<DataAdapter> adapters = new ArrayList<>();

  @XStreamImplicit(itemFieldName = "input")
  private List<XMLInput> inputs = new ArrayList<>();

  public List<DataAdapter> getAdapters() {
    if (adapters == null) {
      adapters = new ArrayList<>();
    }
    return adapters;
  }

  public List<XMLInput> getInputs() {
    return inputs;
  }

  public static XMLConfig parse(File input) {
    XStream stream = XStreamUtils.createXStream();
    stream.setMode(XStream.NO_REFERENCES);
    stream.processAnnotations(XMLConfig.class);
    stream.registerConverter(
        new XMLBindConverter(
            stream.getConverterLookup().lookupConverterForType(XMLBind.class),
            stream.getReflectionProvider()));
    return (XMLConfig) stream.fromXML(input);
  }
}
