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
package com.axelor.data.csv;

import com.axelor.common.VersionUtils;
import com.axelor.data.XStreamUtils;
import com.axelor.data.adapter.DataAdapter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@XStreamAlias("csv-inputs")
public class CSVConfig {

  public static final String NAMESPACE = "http://axelor.com/xml/ns/data-import";

  public static final String VERSION = VersionUtils.getVersion().feature;

  @XStreamImplicit(itemFieldName = "input")
  private List<CSVInput> inputs = new ArrayList<>();

  @XStreamImplicit(itemFieldName = "adapter")
  private List<DataAdapter> adapters = new ArrayList<>();

  /**
   * Get all {@link #inputs} nodes
   *
   * @return the inputs
   */
  public List<CSVInput> getInputs() {
    return inputs;
  }

  /**
   * Set {@link #inputs} nodes
   *
   * @param inputs the inputs
   */
  public void setInputs(List<CSVInput> inputs) {
    this.inputs = inputs;
  }

  /**
   * Get all {@link #adapters} nodes.
   *
   * @return list of all adapters
   */
  public List<DataAdapter> getAdapters() {
    if (adapters == null) {
      adapters = new ArrayList<>();
    }
    return adapters;
  }

  /**
   * Parse the <code>input</code> File
   *
   * @param input the input file
   * @return an instance of {@link CSVConfig} for the given file
   */
  public static CSVConfig parse(File input) {
    XStream stream = XStreamUtils.createXStream();
    stream.processAnnotations(CSVConfig.class);
    stream.registerConverter(
        new CSVInputConverter(
            stream.getConverterLookup().lookupConverterForType(CSVInput.class),
            stream.getReflectionProvider()));
    stream.registerConverter(
        new CSVBindConverter(
            stream.getConverterLookup().lookupConverterForType(CSVBind.class),
            stream.getReflectionProvider()));
    return (CSVConfig) stream.fromXML(input);
  }
}
