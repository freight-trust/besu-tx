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
package tech.pegasys.signers.secp256k1.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordFileUtilTest {

  @ParameterizedTest
  @ValueSource(strings = {"line1\nline2", "line1\n", "line1"})
  void canReadPasswordFromFile(final String content, @TempDir final Path tempDir)
      throws IOException {
    final Path passwordFile = Files.write(tempDir.resolve("password.txt"), content.getBytes(UTF_8));
    assertThat(PasswordFileUtil.readPasswordFromFile(passwordFile)).isEqualTo("line1");
  }

  @Test
  void canReadSpaceOnlyPasswordFromFile(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = Files.write(tempDir.resolve("password.txt"), "   \n".getBytes(UTF_8));
    assertThat(PasswordFileUtil.readPasswordFromFile(passwordFile)).isEqualTo("   ");
  }

  @Test
  void emptyFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = Files.write(tempDir.resolve("password.txt"), new byte[0]);
    assertThatExceptionOfType(SignerInitializationException.class)
        .isThrownBy(() -> PasswordFileUtil.readPasswordFromFile(passwordFile))
        .withMessage("Cannot read password from empty file: %s", passwordFile);
  }

  @Test
  void fileWithEolOnlyThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = Files.write(tempDir.resolve("password.txt"), "\n".getBytes(UTF_8));
    assertThatExceptionOfType(SignerInitializationException.class)
        .isThrownBy(() -> PasswordFileUtil.readPasswordFromFile(passwordFile))
        .withMessage("Cannot read password from empty file: %s", passwordFile);
  }
}
