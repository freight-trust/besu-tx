/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.signers.secp256k1.multikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.signers.secp256k1.multikey.MetadataFileFixture.CONFIG_FILE_EXTENSION;
import static tech.pegasys.signers.secp256k1.multikey.MetadataFileFixture.LOWERCASE_ADDRESS;
import static tech.pegasys.signers.secp256k1.multikey.MetadataFileFixture.LOWER_CASE_PUBLIC_KEY;
import static tech.pegasys.signers.secp256k1.multikey.MetadataFileFixture.copyMetadataFileToDirectory;

import tech.pegasys.signers.secp256k1.EthPublicKeyUtils;
import tech.pegasys.signers.secp256k1.api.FileSelector;
import tech.pegasys.signers.secp256k1.api.Signer;
import tech.pegasys.signers.secp256k1.filebased.FileSignerConfig;
import tech.pegasys.signers.secp256k1.multikey.metadata.FileBasedSigningMetadataFile;
import tech.pegasys.signers.secp256k1.multikey.metadata.SigningMetadataFile;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.ECPublicKey;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiKeySignerProviderTest {

  @TempDir Path configsDirectory;

  private final SigningMetadataTomlConfigLoader loader =
      mock(SigningMetadataTomlConfigLoader.class);

  @Mock private FileSelector<ECPublicKey> fileSelector;
  private MultiKeySignerProvider signerFactory;
  private FileBasedSigningMetadataFile metadataFile;
  private final String KEY_FILENAME = "k.key";
  private final String PASSWORD_FILENAME = "p.password";

  @BeforeEach
  void beforeEach() {
    final Path newKeyFile = configsDirectory.resolve(KEY_FILENAME);
    final Path newPasswordFile = configsDirectory.resolve(PASSWORD_FILENAME);

    metadataFile =
        copyMetadataFileToDirectory(
            configsDirectory,
            LOWERCASE_ADDRESS + CONFIG_FILE_EXTENSION,
            newKeyFile.toAbsolutePath().toString(),
            newPasswordFile.toAbsolutePath().toString());

    // make sure the password files are where they are expected to be
    try {
      Files.copy(
          Path.of(Resources.getResource("metadata-toml-configs").toURI()).resolve(KEY_FILENAME),
          newKeyFile);
      Files.copy(
          Path.of(Resources.getResource("metadata-toml-configs").toURI())
              .resolve(PASSWORD_FILENAME),
          newPasswordFile);
    } catch (Exception e) {
      fail("Error copying metadata files", e);
    }
    signerFactory = new MultiKeySignerProvider(loader, null, null, null, fileSelector);
  }

  @Test
  void getSignerForAvailableMetadataReturnsSigner() {
    when(loader.loadMetadata(any())).thenReturn(Optional.of(metadataFile));

    final Optional<Signer> signer =
        signerFactory.getSigner(
            EthPublicKeyUtils.createPublicKey(Bytes.fromHexString(LOWER_CASE_PUBLIC_KEY)));
    assertThat(signer).isNotEmpty();
    assertThat(EthPublicKeyUtils.toHexString(signer.get().getPublicKey()))
        .isEqualTo("0x" + LOWER_CASE_PUBLIC_KEY);

    final ArgumentCaptor<ECPublicKey> publicKeyCaptor = ArgumentCaptor.forClass(ECPublicKey.class);
    verify(fileSelector).getSpecificConfigFileFilter(publicKeyCaptor.capture());

    assertThat(EthPublicKeyUtils.toHexString(publicKeyCaptor.getValue()))
        .isEqualTo("0x" + LOWER_CASE_PUBLIC_KEY);
  }

  @Test
  void getAddresses() {
    final ImmutableList<SigningMetadataFile> files = ImmutableList.of(metadataFile);
    when(loader.loadAvailableSigningMetadataTomlConfigs(any())).thenReturn(files);
    when(fileSelector.getSpecificConfigFileFilter(any())).thenReturn(entry -> true);
    assertThat(signerFactory.availablePublicKeys().size()).isOne();
    assertThat(signerFactory.availablePublicKeys().stream().map(EthPublicKeyUtils::toHexString))
        .containsExactly("0x" + LOWER_CASE_PUBLIC_KEY);
  }

  @Test
  void signerIsLoadedSuccessfullyWhenAddressHasCaseMismatchToFilename() throws URISyntaxException {
    final FileBasedSigningMetadataFile capitalisedMetadata =
        new FileBasedSigningMetadataFile(
            LOWERCASE_ADDRESS + ".toml",
            new FileSignerConfig(
                Path.of(Resources.getResource("metadata-toml-configs").toURI())
                    .resolve(KEY_FILENAME),
                Path.of(Resources.getResource("metadata-toml-configs").toURI())
                    .resolve(PASSWORD_FILENAME)));

    final Signer signer = signerFactory.createSigner(capitalisedMetadata);
    assertThat(signer).isNotNull();
    assertThat(EthPublicKeyUtils.toHexString(signer.getPublicKey()))
        .isEqualTo("0x" + LOWER_CASE_PUBLIC_KEY);
  }

  @Test
  void signerIsNotReturnedIfFileContainsPublicKeyDifferentToRequest() throws URISyntaxException {

    final FileBasedSigningMetadataFile capitalisedMetadata =
        new FileBasedSigningMetadataFile(
            LOWERCASE_ADDRESS + ".toml",
            new FileSignerConfig(
                Path.of(Resources.getResource("metadata-toml-configs").toURI())
                    .resolve(KEY_FILENAME),
                Path.of(Resources.getResource("metadata-toml-configs").toURI())
                    .resolve(PASSWORD_FILENAME)));

    when(loader.loadMetadata(any())).thenReturn(Optional.of(capitalisedMetadata));

    final Optional<Signer> signer =
        signerFactory.getSigner(
            EthPublicKeyUtils.createPublicKey(Bytes.fromHexString("A".repeat(128))));

    assertThat(signer).isEmpty();
  }
}
