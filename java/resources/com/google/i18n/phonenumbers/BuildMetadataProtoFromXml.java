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

import com.google.i18n.phonenumbers.Phonemetadata.NumberFormat;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadata;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadataCollection;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneNumberDesc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Tool to convert phone number metadata from the XML format to protocol buffer format. It is
 * wrapped in the genrule of the BUILD file and run as a preprocessing step when building the
 * phone number library. Example command line invocation:
 *
 * ./BuildMetadataProtoFromXml PhoneNumberMetadata.xml PhoneNumberMetadataProto true
 *
 * When liteBuild flag is set to true, the outputFile generated omits certain metadata which is not
 * needed for clients using liteBuild. At this moment, example numbers information is omitted.
 *
 * @author Shaopeng Jia
 */
public class BuildMetadataProtoFromXml {
  private BuildMetadataProtoFromXml() {
  }
  private static final Logger LOGGER = Logger.getLogger(BuildMetadataProtoFromXml.class.getName());
  private static Boolean liteBuild;

  // A mapping from a country code to the region codes which denote the country/region
  // represented by that country code. In the case of multiple countries sharing a calling code,
  // such as the NANPA countries, the one indicated with "isMainCountryForCode" in the metadata
  // should be first. The initial capacity is set to 300 as there are roughly 200 different
  // country codes, and this offers a load factor of roughly 0.75.
  private static final HashMap<Integer, List<String> > COUNTRY_CODE_TO_REGION_CODE_MAP =
      new HashMap<Integer, List<String> >(310);

  public static void main(String[] args) throws Exception {
    String inputFile = args[0];
    String filePrefix = args[1];
    String outputMappingFile = filePrefix +
        PhoneNumberUtil.COUNTRY_CODE_TO_REGION_CODE_MAP_FILE_SUFFIX;
    liteBuild = args.length > 2 && Boolean.getBoolean(args[2]);
    File xmlFile = new File(inputFile);

    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = builderFactory.newDocumentBuilder();
    Document document = builder.parse(xmlFile);
    document.getDocumentElement().normalize();
    Element rootElement = document.getDocumentElement();
    NodeList territory = rootElement.getElementsByTagName("territory");
    PhoneMetadataCollection metadataCollection = new PhoneMetadataCollection();
    int numOfTerritories = territory.getLength();
    for (int i = 0; i < numOfTerritories; i++) {
      Element territoryElement = (Element) territory.item(i);
      String regionCode = territoryElement.getAttribute("id");
      PhoneMetadata metadata = loadCountryMetadata(regionCode, territoryElement);
      fillCountryCodeToRegionCodeMap(metadata, regionCode);
      metadataCollection.addMetadata(metadata);
      FileOutputStream outputForRegion = new FileOutputStream(filePrefix + "_" + regionCode);
      ObjectOutputStream out = new ObjectOutputStream(outputForRegion);
      metadataCollection.writeExternal(out);
      out.close();
      metadataCollection.clear();
    }
    writeCountryCallingCodeMappingToFile(outputMappingFile);
  }

  private static void writeCountryCallingCodeMappingToFile(String file) throws IOException {
    BufferedWriter writer =
        new BufferedWriter(new FileWriter(file));
    for (Integer countryCallingCode : COUNTRY_CODE_TO_REGION_CODE_MAP.keySet()) {
      writer.write(countryCallingCode.toString());
      writer.newLine();
      for (String regionCode : COUNTRY_CODE_TO_REGION_CODE_MAP.get(countryCallingCode)) {
        writer.write(' ');
        writer.write(regionCode);
      }
      writer.newLine();
    }
    writer.flush();
    writer.close();
  }

  static void fillCountryCodeToRegionCodeMap(PhoneMetadata metadata, String regionCode) {
    int countryCode = metadata.getCountryCode();
    if (COUNTRY_CODE_TO_REGION_CODE_MAP.containsKey(countryCode)) {
      if (metadata.getMainCountryForCode()) {
        COUNTRY_CODE_TO_REGION_CODE_MAP.get(countryCode).add(0, regionCode);
      } else {
        COUNTRY_CODE_TO_REGION_CODE_MAP.get(countryCode).add(regionCode);
      }
    } else {
      // For most countries, there will be only one region code for the country dialing code.
      List<String> listWithRegionCode = new ArrayList<String>(1);
      listWithRegionCode.add(regionCode);
      COUNTRY_CODE_TO_REGION_CODE_MAP.put(countryCode, listWithRegionCode);
    }
  }

  private static String validateRE(String regex) {
    Pattern.compile(regex);
    // return regex itself if it is of correct regex syntax
    return regex;
  }

  private static PhoneMetadata loadCountryMetadata(String regionCode, Element element) {
    PhoneMetadata metadata = new PhoneMetadata();
    metadata.setId(regionCode);
    metadata.setCountryCode(Integer.parseInt(element.getAttribute("countryCode")));
    if (element.hasAttribute("leadingDigits")) {
      metadata.setLeadingDigits(validateRE(element.getAttribute("leadingDigits")));
    }
    metadata.setInternationalPrefix(validateRE(element.getAttribute("internationalPrefix")));
    if (element.hasAttribute("preferredInternationalPrefix")) {
      String preferredInternationalPrefix = element.getAttribute("preferredInternationalPrefix");
      metadata.setPreferredInternationalPrefix(preferredInternationalPrefix);
    }
    String nationalPrefix = "";
    String nationalPrefixFormattingRule = "";
    String carrierCodeFormattingRule = "";
    if (element.hasAttribute("nationalPrefix")) {
      nationalPrefix = element.getAttribute("nationalPrefix");
      metadata.setNationalPrefix(nationalPrefix);
       nationalPrefixFormattingRule =
          validateRE(getNationalPrefixFormattingRuleFromElement(element, nationalPrefix));

      if (element.hasAttribute("nationalPrefixForParsing")) {
        metadata.setNationalPrefixForParsing(
            validateRE(element.getAttribute("nationalPrefixForParsing")));
        if (element.hasAttribute("nationalPrefixTransformRule")) {
          metadata.setNationalPrefixTransformRule(
              element.getAttribute("nationalPrefixTransformRule"));
        }
      } else {
        metadata.setNationalPrefixForParsing(nationalPrefix);
      }
    }
    if (element.hasAttribute("preferredExtnPrefix")) {
      metadata.setPreferredExtnPrefix(element.getAttribute("preferredExtnPrefix"));
    }

    if (element.hasAttribute("mainCountryForCode")) {
      metadata.setMainCountryForCode(true);
    }

    // Extract availableFormats
    NodeList numberFormatElements = element.getElementsByTagName("numberFormat");
    int numOfFormatElements = numberFormatElements.getLength();
    if (numOfFormatElements > 0) {
      for (int i = 0; i < numOfFormatElements; i++) {
        Element numberFormatElement = (Element) numberFormatElements.item(i);
        NumberFormat format = new NumberFormat();
        if (numberFormatElement.hasAttribute("nationalPrefixFormattingRule")) {
          format.setNationalPrefixFormattingRule(validateRE(
              getNationalPrefixFormattingRuleFromElement(numberFormatElement, nationalPrefix)));
        } else {
          format.setNationalPrefixFormattingRule(nationalPrefixFormattingRule);
        }
        if (numberFormatElement.hasAttribute("carrierCodeFormattingRule")) {
          format.setDomesticCarrierCodeFormattingRule(validateRE(
              getDomesticCarrierCodeFormattingRuleFromElement(numberFormatElement,
                                                              nationalPrefix)));
        } else {
          format.setDomesticCarrierCodeFormattingRule(carrierCodeFormattingRule);
        }
        setLeadingDigitsPatterns(numberFormatElement, format);
        format.setPattern(validateRE(numberFormatElement.getAttribute("pattern")));
        NodeList formatPattern = numberFormatElement.getElementsByTagName("format");
        if (formatPattern.getLength() != 1) {
          LOGGER.log(Level.SEVERE,
                     "Only one format pattern for a numberFormat element should be defined.");
          throw new RuntimeException("Invalid number of format patterns for country: " +
                                     regionCode);
        }
        format.setFormat(validateRE(formatPattern.item(0).getFirstChild().getNodeValue()));
        metadata.addNumberFormat(format);
      }
    }

    NodeList intlNumberFormatElements = element.getElementsByTagName("intlNumberFormat");
    int numOfIntlFormatElements = intlNumberFormatElements.getLength();
    if (numOfIntlFormatElements > 0) {
      for (int i = 0; i < numOfIntlFormatElements; i++) {
        Element numberFormatElement = (Element) intlNumberFormatElements.item(i);
        NumberFormat format = new NumberFormat();
      setLeadingDigitsPatterns(numberFormatElement, format);
        format.setPattern(validateRE(numberFormatElement.getAttribute("pattern")));
        NodeList formatPattern = numberFormatElement.getElementsByTagName("format");
        if (formatPattern.getLength() != 1) {
          LOGGER.log(Level.SEVERE,
                     "Only one format pattern for a numberFormat element should be defined.");
          throw new RuntimeException("Invalid number of format patterns for country: " +
                                     regionCode);
        }
        format.setFormat(validateRE(formatPattern.item(0).getFirstChild().getNodeValue()));
        if (numberFormatElement.hasAttribute("carrierCodeFormattingRule")) {
          format.setDomesticCarrierCodeFormattingRule(validateRE(
              getDomesticCarrierCodeFormattingRuleFromElement(numberFormatElement,
                                                              nationalPrefix)));
        } else {
          format.setDomesticCarrierCodeFormattingRule(carrierCodeFormattingRule);
        }
        metadata.addIntlNumberFormat(format);
      }
    }

    PhoneNumberDesc generalDesc =
        processPhoneNumberDescElement(new PhoneNumberDesc(), element, "generalDesc");
    metadata.setGeneralDesc(generalDesc);
    metadata.setFixedLine(processPhoneNumberDescElement(generalDesc, element, "fixedLine"));
    metadata.setMobile(processPhoneNumberDescElement(generalDesc, element, "mobile"));
    metadata.setTollFree(processPhoneNumberDescElement(generalDesc, element, "tollFree"));
    metadata.setPremiumRate(processPhoneNumberDescElement(generalDesc, element, "premiumRate"));
    metadata.setSharedCost(processPhoneNumberDescElement(generalDesc, element, "sharedCost"));
    metadata.setVoip(processPhoneNumberDescElement(generalDesc, element, "voip"));
    metadata.setPersonalNumber(processPhoneNumberDescElement(generalDesc, element,
                                                             "personalNumber"));

    if (metadata.getMobile().getNationalNumberPattern().equals(
        metadata.getFixedLine().getNationalNumberPattern())) {
      metadata.setSameMobileAndFixedLinePattern(true);
    }
    return metadata;
  }

  private static void setLeadingDigitsPatterns(Element numberFormatElement, NumberFormat format) {
    NodeList leadingDigitsPatternNodes = numberFormatElement.getElementsByTagName("leadingDigits");
    int numOfLeadingDigitsPatterns = leadingDigitsPatternNodes.getLength();
    if (numOfLeadingDigitsPatterns > 0) {
      for (int i = 0; i < numOfLeadingDigitsPatterns; i++) {
        format.addLeadingDigitsPattern(
            validateRE((leadingDigitsPatternNodes.item(i)).getFirstChild().getNodeValue()));
      }
    }
  }

  private static String getNationalPrefixFormattingRuleFromElement(Element element,
                                                                   String nationalPrefix) {
    String nationalPrefixFormattingRule = element.getAttribute("nationalPrefixFormattingRule");
    // Replace $NP with national prefix and $FG with the first group ($1).
    nationalPrefixFormattingRule =
        nationalPrefixFormattingRule.replaceFirst("\\$NP", nationalPrefix)
            .replaceFirst("\\$FG", "\\$1");
    return nationalPrefixFormattingRule;
  }

  private static String getDomesticCarrierCodeFormattingRuleFromElement(Element element,
                                                                        String nationalPrefix) {
    String carrierCodeFormattingRule = element.getAttribute("carrierCodeFormattingRule");
    // Replace $FG with the first group ($1) and $NP with the national prefix.
    carrierCodeFormattingRule = carrierCodeFormattingRule.replaceFirst("\\$FG", "\\$1")
        .replaceFirst("\\$NP", nationalPrefix);
    return carrierCodeFormattingRule;
  }

  /**
   * Processes a phone number description element from the XML file and returns it as a
   * PhoneNumberDesc. If the description element is a fixed line or mobile number, the general
   * description will be used to fill in the whole element if necessary, or any components that are
   * missing. For all other types, the general description will only be used to fill in missing
   * components if the type has a partial definition. For example, if no "tollFree" element exists,
   * we assume there are no toll free numbers for that locale, and return a phone number description
   * with "NA" for both the national and possible number patterns.
   *
   * @param generalDesc  a generic phone number description that will be used to fill in missing
   *                     parts of the description
   * @param countryElement  the XML element representing all the country information
   * @param numberType  the name of the number type, corresponding to the appropriate tag in the XML
   *                    file with information about that type
   * @return  complete description of that phone number type
   */
  private static PhoneNumberDesc processPhoneNumberDescElement(PhoneNumberDesc generalDesc,
                                                               Element countryElement,
                                                               String numberType) {
    NodeList phoneNumberDescList = countryElement.getElementsByTagName(numberType);
    PhoneNumberDesc numberDesc = new PhoneNumberDesc();
    if (phoneNumberDescList.getLength() == 0 &&
        (!numberType.equals("fixedLine") && !numberType.equals("mobile") &&
         !numberType.equals("generalDesc"))) {
      numberDesc.setNationalNumberPattern("NA");
      numberDesc.setPossibleNumberPattern("NA");
      return numberDesc;
    }
    numberDesc.mergeFrom(generalDesc);
    if (phoneNumberDescList.getLength() > 0) {
      Element element = (Element) phoneNumberDescList.item(0);
      NodeList possiblePattern = element.getElementsByTagName("possibleNumberPattern");
      if (possiblePattern.getLength() > 0) {
        numberDesc.setPossibleNumberPattern(
            validateRE(possiblePattern.item(0).getFirstChild().getNodeValue()));
      }

      NodeList validPattern = element.getElementsByTagName("nationalNumberPattern");
      if (validPattern.getLength() > 0) {
        numberDesc.setNationalNumberPattern(
            validateRE(validPattern.item(0).getFirstChild().getNodeValue()));
      }

      if (!liteBuild) {
        NodeList exampleNumber = element.getElementsByTagName("exampleNumber");
        if (exampleNumber.getLength() > 0) {
          numberDesc.setExampleNumber(exampleNumber.item(0).getFirstChild().getNodeValue());
        }
      }
    }
    return numberDesc;
  }
}
