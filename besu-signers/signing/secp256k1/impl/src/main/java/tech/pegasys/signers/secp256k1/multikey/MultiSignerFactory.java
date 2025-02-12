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

import tech.pegasys.signers.secp256k1.api.Signer;
import tech.pegasys.signers.secp256k1.multikey.metadata.AzureSigningMetadataFile;
import tech.pegasys.signers.secp256k1.multikey.metadata.CaviumSigningMetadataFile;
import tech.pegasys.signers.secp256k1.multikey.metadata.FileBasedSigningMetadataFile;
import tech.pegasys.signers.secp256k1.multikey.metadata.HSMSigningMetadataFile;
import tech.pegasys.signers.secp256k1.multikey.metadata.HashicorpSigningMetadataFile;
import tech.pegasys.signers.secp256k1.multikey.metadata.RawSigningMetadataFile;

public interface MultiSignerFactory {

  Signer createSigner(AzureSigningMetadataFile metadataFile);

  Signer createSigner(FileBasedSigningMetadataFile metadataFile);

  Signer createSigner(HashicorpSigningMetadataFile metadataFile);

  Signer createSigner(RawSigningMetadataFile metadataFile);

  Signer createSigner(HSMSigningMetadataFile metadataFile);

  Signer createSigner(CaviumSigningMetadataFile metadataFile);
}
