/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.wallet.nameservice.data.mapper

import com.algorand.wallet.nameservice.data.model.NameServiceResult
import com.algorand.wallet.nameservice.domain.model.NameService
import com.algorand.wallet.nameservice.domain.model.NameServiceSource
import javax.inject.Inject

internal class NameServiceMapperImpl @Inject constructor() : NameServiceMapper {

    override fun invoke(results: List<NameServiceResult>): List<NameService> {
        return results.mapNotNull {
            NameService(
                accountAddress = it.address,
                nameServiceName = it.name,
                nameServiceSource = NameServiceSource.NFDOMAIN,
                nameServiceUri = it.metadata?.avatar
            )
        }
    }
}
