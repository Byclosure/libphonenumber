/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.i18n.phonenumbers;

import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadata;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadataCollection;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

/**
 * Tool to convert phone number metadata from the XML format to protocol buffer format.
 *
 * @author Shaopeng Jia
 */
public class BuildMetadataProtoFromXml {
  private static final String PACKAGE_NAME = PhoneNumberUtil.class.getPackage().getName();

  private static final String HELP_MESSAGE =
      "Usage:\n" +
      "BuildMetadataProtoFromXml <inputFile> <outputDir> <forTesting> [<liteBuild>]\n" +
      "\n" +
      "where:\n" +
      "  inputFile    The input file containing phone number metadata in XML format.\n" +
      "  outputDir    The output directory to store phone number metadata in proto\n" +
      "               format (one file per region) and the country code to region code\n" +
      "               mapping file.\n" +
      "  forTesting   Flag whether to generate metadata for testing purposes or not.\n" +
      "  liteBuild    Whether to generate the lite-version of the metadata (default:\n" +
      "               false). When set to true certain metadata will be omitted.\n" +
      "               At this moment, example numbers information is omitted.\n" +
      "\n" +
      "Metadata will be stored in:\n" +
      "  <outputDir>" + PhoneNumberUtil.META_DATA_FILE_PREFIX + "_*\n" +
      "Mapping file will be stored in:\n" +
      "  <outputDir>/" + PACKAGE_NAME.replaceAll("\\.", "/") + "/" +
          PhoneNumberUtil.COUNTRY_CODE_TO_REGION_CODE_MAP_CLASS_NAME + ".java\n" +
      "\n" +
      "Example command line invocation:\n" +
      "BuildMetadataProtoFromXml PhoneNumberMetadata.xml src false false\n";

  public static void main(String[] args) throws Exception {
    if (args.length != 3 && args.length != 4) {
      System.err.println(HELP_MESSAGE);
      System.exit(1);
    }
    String inputFile = args[0];
    String outputDir = args[1];
    boolean forTesting = args[2].equals("true");
    boolean liteBuild = args.length > 3 && args[3].equals("true");
    
    String filePrefix;
    if (forTesting) {
      filePrefix = outputDir + PhoneNumberUtilTest.TEST_META_DATA_FILE_PREFIX;
    } else {
      filePrefix = outputDir + PhoneNumberUtil.META_DATA_FILE_PREFIX;
    }

    PhoneMetadataCollection metadataCollection =
        BuildMetadataFromXml.buildPhoneMetadataCollection(inputFile, liteBuild);

    for (PhoneMetadata metadata : metadataCollection.getMetadataList()) {
      String regionCode = metadata.getId();
      PhoneMetadataCollection outMetadataCollection = new PhoneMetadataCollection();
      outMetadataCollection.addMetadata(metadata);
      FileOutputStream outputForRegion = new FileOutputStream(filePrefix + "_" + regionCode);
      ObjectOutputStream out = new ObjectOutputStream(outputForRegion);
      outMetadataCollection.writeExternal(out);
      out.close();
    }

    Map<Integer, List<String>> countryCodeToRegionCodeMap =
        BuildMetadataFromXml.buildCountryCodeToRegionCodeMap(metadataCollection);

    writeCountryCallingCodeMappingToJavaFile(countryCodeToRegionCodeMap, outputDir, forTesting);
  }

  static final String COPYRIGHT_NOTICE =
      "/*\n" +
      " * Copyright (C) 2010 Google Inc.\n" +
      " *\n" +
      " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
      " * you may not use this file except in compliance with the License.\n" +
      " * You may obtain a copy of the License at\n" +
      " *\n" +
      " * http://www.apache.org/licenses/LICENSE-2.0\n" +
      " *\n" +
      " * Unless required by applicable law or agreed to in writing, software\n" +
      " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
      " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
      " * See the License for the specific language governing permissions and\n" +
      " * limitations under the License.\n" +
      " */\n\n";
  private static final String MAPPING_IMPORTS =
      "import java.util.ArrayList;\n" +
      "import java.util.HashMap;\n" +
      "import java.util.List;\n" +
      "import java.util.Map;\n";
  private static final String MAPPING_COMMENT =
      "  // A mapping from a country code to the region codes which denote the\n" +
      "  // country/region represented by that country code. In the case of multiple\n" +
      "  // countries sharing a calling code, such as the NANPA countries, the one\n" +
      "  // indicated with \"isMainCountryForCode\" in the metadata should be first.\n";
  private static final double MAPPING_LOAD_FACTOR = 0.75;
  private static final String MAPPING_COMMENT_2 =
      "    // The capacity is set to %d as there are %d different country codes,\n" +
      "    // and this offers a load factor of roughly " + MAPPING_LOAD_FACTOR + ".\n";

  private static void writeCountryCallingCodeMappingToJavaFile(
      Map<Integer, List<String>> countryCodeToRegionCodeMap,
      String outputDir, boolean forTesting) throws IOException {
    String mappingClassName;
    if (forTesting) {
      mappingClassName = PhoneNumberUtilTest.TEST_COUNTRY_CODE_TO_REGION_CODE_MAP_CLASS_NAME;
    } else {
      mappingClassName = PhoneNumberUtil.COUNTRY_CODE_TO_REGION_CODE_MAP_CLASS_NAME;
    }
    String mappingFile =
        outputDir + "/" + PACKAGE_NAME.replaceAll("\\.", "/") + "/" + mappingClassName + ".java";
    int capacity = (int) (countryCodeToRegionCodeMap.size() / MAPPING_LOAD_FACTOR);

    BufferedWriter writer = new BufferedWriter(new FileWriter(mappingFile));

    writer.write(COPYRIGHT_NOTICE);
    if (PACKAGE_NAME.length() > 0) {
      writer.write("package " + PACKAGE_NAME + ";\n\n");
    }
    writer.write(MAPPING_IMPORTS);
    writer.write("\n");
    writer.write("public class " + mappingClassName + " {\n");
    writer.write(MAPPING_COMMENT);
    writer.write("  static Map<Integer, List<String>> getCountryCodeToRegionCodeMap() {\n");
    Formatter formatter = new Formatter(writer);
    formatter.format(MAPPING_COMMENT_2, capacity, countryCodeToRegionCodeMap.size());
    writer.write("    Map<Integer, List<String>> countryCodeToRegionCodeMap =\n");
    writer.write("        new HashMap<Integer, List<String>>(" + capacity + ");\n");
    writer.write("\n");
    writer.write("    ArrayList<String> listWithRegionCode;\n");
    writer.write("\n");

    for (Map.Entry<Integer, List<String>> entry : countryCodeToRegionCodeMap.entrySet()) {
      int countryCallingCode = entry.getKey();
      List<String> regionCodes = entry.getValue();
      writer.write("    listWithRegionCode = new ArrayList<String>(" + regionCodes.size() + ");\n");
      for (String regionCode : regionCodes) {
        writer.write("    listWithRegionCode.add(\"" + regionCode + "\");\n");
      }
      writer.write("    countryCodeToRegionCodeMap.put(" + countryCallingCode +
                   ", listWithRegionCode);\n");
      writer.write("\n");
    }

    writer.write("    return countryCodeToRegionCodeMap;\n");
    writer.write("  }\n");
    writer.write("}\n");

    writer.flush();
    writer.close();
  }
}
