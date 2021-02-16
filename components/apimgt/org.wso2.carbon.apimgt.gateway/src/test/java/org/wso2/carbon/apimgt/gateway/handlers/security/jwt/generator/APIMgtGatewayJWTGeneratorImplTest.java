/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.apimgt.gateway.handlers.security.jwt.generator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.wso2.carbon.apimgt.common.gateway.jwtgenerator.APIMgtGatewayJWTGeneratorImpl;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.APIManagerConfigurationService;
import org.wso2.carbon.apimgt.impl.dto.ExtendedJWTConfigurationDto;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.common.gateway.jwtgenerator.AbstractAPIMgtGatewayJWTGenerator;
import org.wso2.carbon.apimgt.impl.token.ClaimsRetriever;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceReferenceHolder.class})
public class APIMgtGatewayJWTGeneratorImplTest {

    @Test
    public void testJWTGeneratorClaimConfig() {

        APIManagerConfiguration apiManagerConfiguration = Mockito.mock(APIManagerConfiguration.class);
        APIManagerConfigurationService apiManagerConfigurationService = Mockito
                .mock(APIManagerConfigurationService.class);
        ServiceReferenceHolder serviceReferenceHolder = Mockito.mock(ServiceReferenceHolder.class);
        PowerMockito.mockStatic(ServiceReferenceHolder.class);
        PowerMockito.when(ServiceReferenceHolder.getInstance()).thenReturn(serviceReferenceHolder);
        Mockito.when(serviceReferenceHolder.getAPIManagerConfigurationService())
                .thenReturn(apiManagerConfigurationService);
        Mockito.when(apiManagerConfigurationService.getAPIManagerConfiguration()).thenReturn(apiManagerConfiguration);

        ExtendedJWTConfigurationDto jwtConfigurationDto = Mockito.mock(ExtendedJWTConfigurationDto.class);
        Mockito.when(apiManagerConfiguration.getJwtConfigurationDto()).thenReturn(jwtConfigurationDto);

        String defaultDialectUri = "http://wso2.org/claims";
        // default dialect if not changed
        AbstractAPIMgtGatewayJWTGenerator generator = new APIMgtGatewayJWTGeneratorImpl();
        // Set jwtConfigurationDto
        generator.setJWTConfigurationDto(jwtConfigurationDto);
        Assert.assertEquals(generator.getDialectURI(), ClaimsRetriever.DEFAULT_DIALECT_URI);

        // Test dialect after changing it through config
        String claimDialect = "http://test.com";
        Mockito.when(jwtConfigurationDto.getConsumerDialectUri()).thenReturn(claimDialect);
        generator = new APIMgtGatewayJWTGeneratorImpl();
        generator.setJWTConfigurationDto(jwtConfigurationDto);
        Assert.assertEquals(generator.getDialectURI(), claimDialect);
    }
}
