/*
 * Copyright (C) 2010 Google Inc.
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

import com.google.i18n.phonenumbers.Phonemetadata.NumberFormat;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadata;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadataCollection;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneNumberDesc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

/**
 * Tool to convert phone number metadata from the XML format to JSON format.
 *
 * @author Nikolaos Trogkanis
 */
public class BuildMetadataJsonFromXml {
  private static final String NAMESPACE = "i18n.phonenumbers.metadata";

  private static final String HELP_MESSAGE =
      "Usage:\n" +
      "BuildMetadataJsonFromXml <inputFile> <outputFile> [<liteBuild>]\n" +
      "\n" +
      "where:\n" +
      "  inputFile    The input file containing phone number metadata in XML format.\n" +
      "  outputFile   The output file to contain phone number metadata in JSON format.\n" +
      "  liteBuild    Whether to generate the lite-version of the metadata (default:\n" +
      "               false). When set to true certain metadata will be omitted.\n" +
      "               At this moment, example numbers information is omitted.\n" +
      "\n" +
      "Example command line invocation:\n" +
      "BuildMetadataJsonFromXml PhoneNumberMetadata.xml metadatalite.js true\n";

  static final String COPYRIGHT_NOTICE =
      "/*\n" +
      " * @license\n" +
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

  private static final String FILE_OVERVIEW =
      "/**\n" +
      " * @fileoverview Generated metadata for file\n" +
      " * %s\n" +
      " * @author Nikolaos Trogkanis\n" +
      " */\n\n";

  private static final String COUNTRY_CODE_TO_REGION_CODE_MAP_COMMENT =
      "/**\n" +
      " * A mapping from a country code to the region codes which denote the\n" +
      " * country/region represented by that country code. In the case of multiple\n" +
      " * countries sharing a calling code, such as the NANPA countries, the one\n" +
      " * indicated with \"isMainCountryForCode\" in the metadata should be first.\n" +
      " * @type {Object.<number, Array.<string>>}\n" +
      " */\n";

  private static final String COUNTRY_TO_METADATA_COMMENT =
      "/**\n" +
      " * A mapping from a region code to the PhoneMetadata for that region.\n" +
      " * @type {Object.<string, Array>}\n" +
      " */\n";

  public static void main(String[] args) throws Exception {
    if (args.length != 2 && args.length != 3) {
      System.err.println(HELP_MESSAGE);
      System.exit(1);
    }
    String inputFile = args[0];
    String outputFile = args[1];
    boolean liteBuild = args.length > 2 && args[2].equals("true");

    PhoneMetadataCollection metadataCollection =
        BuildMetadataFromXml.buildPhoneMetadataCollection(inputFile, liteBuild);
    Map<Integer, List<String>> countryCodeToRegionCodeMap =
        BuildMetadataFromXml.buildCountryCodeToRegionCodeMap(metadataCollection);

    BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

    writer.write(COPYRIGHT_NOTICE);
    Formatter formatter = new Formatter(writer);
    formatter.format(FILE_OVERVIEW, inputFile);

    writer.write("goog.provide('" + NAMESPACE + "');\n\n");

    writer.write(COUNTRY_CODE_TO_REGION_CODE_MAP_COMMENT);
    writer.write(NAMESPACE + ".countryCodeToRegionCodeMap = ");
    writeCountryCodeCodeToRegionCodeMap(countryCodeToRegionCodeMap, writer);
    writer.write(";\n\n");

    writer.write(COUNTRY_TO_METADATA_COMMENT);
    writer.write(NAMESPACE + ".countryToMetadata = ");
    writeCountryToMetadataMap(metadataCollection, writer);
    writer.write(";\n");

    writer.flush();
    writer.close();
  }

  // Writes a PhoneMetadataCollection in JSON format.
  private static void writeCountryToMetadataMap(PhoneMetadataCollection metadataCollection,
                                                BufferedWriter writer) throws IOException {
    writer.write("{\n");
    boolean isFirstTimeInLoop = true;
    for (PhoneMetadata metadata : metadataCollection.getMetadataList()) {
      if (isFirstTimeInLoop) {
        isFirstTimeInLoop = false;
      } else {
        writer.write(",");
      }
      String regionCode = metadata.getId();
      JSArrayBuilder jsArrayBuilder = new JSArrayBuilder();
      toJsArray(metadata, jsArrayBuilder);
      writer.write("\"");
      writer.write(regionCode);
      writer.write("\":");
      writer.write(jsArrayBuilder.toString());
    }
    writer.write("}");
  }

  // Writes a Map<Integer, List<String>> in JSON format.
  private static void writeCountryCodeCodeToRegionCodeMap(
      Map<Integer, List<String>> countryCodeToRegionCodeMap,
      BufferedWriter writer) throws IOException {
    writer.write("{\n");
    boolean isFirstTimeInLoop = true;
    for (Map.Entry<Integer, List<String>> entry : countryCodeToRegionCodeMap.entrySet()) {
      if (isFirstTimeInLoop) {
        isFirstTimeInLoop = false;
      } else {
        writer.write(",");
      }
      writer.write(Integer.toString(entry.getKey()));
      writer.write(":");
      JSArrayBuilder jsArrayBuilder = new JSArrayBuilder();
      jsArrayBuilder.beginArray();
      jsArrayBuilder.appendIterator(entry.getValue().iterator());
      jsArrayBuilder.endArray();
      writer.write(jsArrayBuilder.toString());
    }
    writer.write("}");
  }

  // Converts NumberFormat to JSArray.
  private static void toJsArray(NumberFormat format, JSArrayBuilder jsArrayBuilder) {
    jsArrayBuilder.beginArray();

    // missing 0
    jsArrayBuilder.append(null);
    // required string pattern = 1;
    jsArrayBuilder.append(format.getPattern());
    // required string format = 2;
    jsArrayBuilder.append(format.getFormat());
    // repeated string leading_digits_pattern = 3;
    int leadingDigitsPatternSize = format.getLeadingDigitsPatternCount();
    if (leadingDigitsPatternSize > 0) {
      jsArrayBuilder.beginArray();
      for (int i = 0; i < leadingDigitsPatternSize; i++) {
        jsArrayBuilder.append(format.getLeadingDigitsPattern(i));
      }
      jsArrayBuilder.endArray();
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string national_prefix_formatting_rule = 4;
    if (format.hasNationalPrefixFormattingRule()) {
      jsArrayBuilder.append(format.getNationalPrefixFormattingRule());
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string domestic_carrier_code_formatting_rule = 5;
    if (format.hasDomesticCarrierCodeFormattingRule()) {
      jsArrayBuilder.append(format.getDomesticCarrierCodeFormattingRule());
    } else {
      jsArrayBuilder.append(null);
    }

    jsArrayBuilder.endArray();
  }

  // Converts PhoneNumberDesc to JSArray.
  private static void toJsArray(PhoneNumberDesc desc, JSArrayBuilder jsArrayBuilder) {
    jsArrayBuilder.beginArray();

    // missing 0
    jsArrayBuilder.append(null);
    // missing 1
    jsArrayBuilder.append(null);
    // optional string national_number_pattern = 2;
    if (desc.hasNationalNumberPattern()) {
      jsArrayBuilder.append(desc.getNationalNumberPattern());
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string possible_number_pattern = 3;
    if (desc.hasPossibleNumberPattern()) {
      jsArrayBuilder.append(desc.getPossibleNumberPattern());
    } else {
      jsArrayBuilder.append(null);
    }
    // missing 4
    jsArrayBuilder.append(null);
    // missing 5
    jsArrayBuilder.append(null);
    // optional string example_number = 6;
    if (desc.hasExampleNumber()) {
      jsArrayBuilder.append(desc.getExampleNumber());
    } else {
      jsArrayBuilder.append(null);
    }

    jsArrayBuilder.endArray();
  }

  // Converts PhoneMetadata to JSArray.
  private static void toJsArray(PhoneMetadata metadata, JSArrayBuilder jsArrayBuilder) {
    jsArrayBuilder.beginArray();

    // missing 0
    jsArrayBuilder.append(null);
    // required PhoneNumberDesc general_desc = 1;
    if (metadata.hasGeneralDesc()) {
      toJsArray(metadata.getGeneralDesc(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc fixed_line = 2;
    if (metadata.hasFixedLine()) {
      toJsArray(metadata.getFixedLine(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc mobile = 3;
    if (metadata.hasMobile()) {
      toJsArray(metadata.getMobile(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc toll_free = 4;
    if (metadata.hasTollFree()) {
      toJsArray(metadata.getTollFree(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc premium_rate = 5;
    if (metadata.hasPremiumRate()) {
      toJsArray(metadata.getPremiumRate(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc shared_cost = 6;
    if (metadata.hasSharedCost()) {
      toJsArray(metadata.getSharedCost(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc personal_number = 7;
    if (metadata.hasPersonalNumber()) {
      toJsArray(metadata.getPersonalNumber(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }
    // required PhoneNumberDesc voip = 8;
    if (metadata.hasVoip()) {
      toJsArray(metadata.getVoip(), jsArrayBuilder);
    } else {
      jsArrayBuilder.append(null);
    }

    // required string id = 9;
    jsArrayBuilder.append(metadata.getId());
    // required int32 country_code = 10;
    jsArrayBuilder.append(metadata.getCountryCode());
    // required string international_prefix = 11;
    jsArrayBuilder.append(metadata.getInternationalPrefix());

    // optional string national_prefix = 12;
    if (metadata.hasNationalPrefix()) {
      jsArrayBuilder.append(metadata.getNationalPrefix());
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string preferred_extn_prefix = 13;
    if (metadata.hasPreferredExtnPrefix()) {
      jsArrayBuilder.append(metadata.getPreferredExtnPrefix());
    } else {
      jsArrayBuilder.append(null);
    }
    // missing 14
    jsArrayBuilder.append(null);
    // optional string national_prefix_for_parsing = 15;
    if (metadata.hasNationalPrefixForParsing()) {
      jsArrayBuilder.append(metadata.getNationalPrefixForParsing());
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string national_prefix_transform_rule = 16;
    if (metadata.hasNationalPrefixTransformRule()) {
      jsArrayBuilder.append(metadata.getNationalPrefixTransformRule());
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string preferred_international_prefix = 17;
    if (metadata.hasPreferredInternationalPrefix()) {
      jsArrayBuilder.append(metadata.getPreferredInternationalPrefix());
    } else {
      jsArrayBuilder.append(null);
    }
    // optional bool same_mobile_and_fixed_line_pattern = 18 [default=false];
    if (metadata.getSameMobileAndFixedLinePattern()) {
      jsArrayBuilder.append(1);
    } else {
      jsArrayBuilder.append(null);
    }
    // repeated NumberFormat number_format = 19;
    int numberFormatSize = metadata.getNumberFormatCount();
    if (numberFormatSize > 0) {
      jsArrayBuilder.beginArray();
      for (int i = 0; i < numberFormatSize; i++) {
        toJsArray(metadata.getNumberFormat(i), jsArrayBuilder);
      }
      jsArrayBuilder.endArray();
    } else {
      jsArrayBuilder.append(null);
    }
    // repeated NumberFormat intl_number_format = 20;
    int intlNumberFormatSize = metadata.getIntlNumberFormatCount();
    if (intlNumberFormatSize > 0) {
      jsArrayBuilder.beginArray();
      for (int i = 0; i < intlNumberFormatSize; i++) {
        toJsArray(metadata.getIntlNumberFormat(i), jsArrayBuilder);
      }
      jsArrayBuilder.endArray();
    } else {
      jsArrayBuilder.append(null);
    }
    // missing 21
    jsArrayBuilder.append(null);
    // optional bool main_country_for_code = 22 [default=false];
    if (metadata.getMainCountryForCode()) {
      jsArrayBuilder.append(1);
    } else {
      jsArrayBuilder.append(null);
    }
    // optional string leading_digits = 23;
    if (metadata.hasLeadingDigits()) {
      jsArrayBuilder.append(metadata.getLeadingDigits());
    } else {
      jsArrayBuilder.append(null);
    }

    jsArrayBuilder.endArray();
  }
}
