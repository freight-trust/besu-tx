/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.signers.yubihsm2;

public enum OutputFormat {
  DEFAULT("default"),
  BASE64("base64"),
  BINARY("binary"),
  PEM("PEM"),
  HEX("hex"),
  ASCII("ASCII");

  private final String value;

  OutputFormat(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
