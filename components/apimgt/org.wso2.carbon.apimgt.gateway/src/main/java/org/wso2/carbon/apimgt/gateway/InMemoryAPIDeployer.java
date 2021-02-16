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

package org.wso2.carbon.apimgt.gateway;

import com.google.gson.Gson;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.wso2.carbon.apimgt.api.gateway.GatewayAPIDTO;
import org.wso2.carbon.apimgt.api.gateway.GatewayContentDTO;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.APIStatus;
import org.wso2.carbon.apimgt.gateway.internal.DataHolder;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.gateway.service.APIGatewayAdmin;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.GatewayArtifactSynchronizerProperties;
import org.wso2.carbon.apimgt.impl.dto.GatewayCleanupSkipList;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.ArtifactRetriever;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.exception.ArtifactSynchronizerException;
import org.wso2.carbon.apimgt.impl.notifier.events.APIEvent;
import org.wso2.carbon.apimgt.impl.notifier.events.DeployAPIInGatewayEvent;
import org.wso2.carbon.apimgt.impl.utils.GatewayUtils;
import org.wso2.carbon.apimgt.keymgt.SubscriptionDataHolder;
import org.wso2.carbon.apimgt.keymgt.model.SubscriptionDataStore;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * This class contains the methods used to retrieve artifacts from a storage and deploy and undeploy the API in gateway
 */
public class InMemoryAPIDeployer {

    private static Log log = LogFactory.getLog(InMemoryAPIDeployer.class);
    private boolean debugEnabled = log.isDebugEnabled();
    ArtifactRetriever artifactRetriever;
    GatewayArtifactSynchronizerProperties gatewayArtifactSynchronizerProperties;

    public InMemoryAPIDeployer() {

        this.artifactRetriever = ServiceReferenceHolder.getInstance().getArtifactRetriever();
        this.gatewayArtifactSynchronizerProperties = ServiceReferenceHolder
                .getInstance().getAPIManagerConfiguration().getGatewayArtifactSynchronizerProperties();
    }

    /**
     * Deploy an API in the gateway using the deployAPI method in gateway admin
     *
     * @param apiId         - UUID of the API
     * @param gatewayLabels - Labels of the Gateway
     * @return True if API artifact retrieved from the storage and successfully deployed without any error. else false
     */
    public boolean deployAPI(String apiId, Set<String> gatewayLabels) throws ArtifactSynchronizerException {

        String labelString = String.join("|", gatewayLabels);
        String encodedString = Base64.encodeBase64URLSafeString(labelString.getBytes());
        if (artifactRetriever != null) {
            try {
                String gatewayRuntimeArtifact = artifactRetriever.retrieveArtifact(apiId, encodedString);
                if (StringUtils.isNotEmpty(gatewayRuntimeArtifact)) {
                    GatewayAPIDTO gatewayAPIDTO = new Gson().fromJson(gatewayRuntimeArtifact, GatewayAPIDTO.class);
                    APIGatewayAdmin apiGatewayAdmin = new APIGatewayAdmin();
                    MessageContext.setCurrentMessageContext(org.wso2.carbon.apimgt.gateway.utils.GatewayUtils.createAxis2MessageContext());
                    apiGatewayAdmin.deployAPI(gatewayAPIDTO);
                    addDeployedCertificatesToAPIAssociation(gatewayAPIDTO);
                    if (debugEnabled) {
                        log.debug(
                                "API with " + apiId + " is deployed in gateway with the labels " + String.join(","
                                        , gatewayLabels));
                    }
                    return true;
                } else {
                    String msg = "Error retrieving artifacts for API " + apiId + ". Storage returned null";
                    log.error(msg);
                    throw new ArtifactSynchronizerException(msg);
                }
            } catch (IOException | ArtifactSynchronizerException e) {
                String msg = "Error deploying " + apiId + " in Gateway";
                log.error(msg, e);
                throw new ArtifactSynchronizerException(msg, e);
            } finally {
                MessageContext.destroyCurrentMessageContext();
            }
        } else {
            String msg = "Artifact retriever not found";
            log.error(msg);
            throw new ArtifactSynchronizerException(msg);
        }
    }

    /**
     * Deploy an API in the gateway using the deployAPI method in gateway admin
     *
     * @param assignedGatewayLabels - The labels which the gateway subscribed to
     * @param tenantDomain
     * @return True if all API artifacts retrieved from the storage and successfully deployed without any error. else
     * false
     */
    public boolean deployAllAPIsAtGatewayStartup(Set<String> assignedGatewayLabels, String tenantDomain) throws
                                                                                                         ArtifactSynchronizerException {

        if (gatewayArtifactSynchronizerProperties.isRetrieveFromStorageEnabled()) {
            if (artifactRetriever != null) {
                try {
                    String labelString = String.join("|", assignedGatewayLabels);
                    String encodedString = Base64.encodeBase64URLSafeString(labelString.getBytes());
                    APIGatewayAdmin apiGatewayAdmin = new APIGatewayAdmin();
                    MessageContext.setCurrentMessageContext(org.wso2.carbon.apimgt.gateway.utils.GatewayUtils.createAxis2MessageContext());
                    PrivilegedCarbonContext.startTenantFlow();
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                    List<String> gatewayRuntimeArtifacts = ServiceReferenceHolder
                            .getInstance().getArtifactRetriever().retrieveAllArtifacts(encodedString, tenantDomain);
                    for (String runtimeArtifact : gatewayRuntimeArtifacts) {
                        GatewayAPIDTO gatewayAPIDTO = null;
                        try {
                            if (StringUtils.isNotEmpty(runtimeArtifact)) {
                                gatewayAPIDTO = new Gson().fromJson(runtimeArtifact, GatewayAPIDTO.class);
                                log.info("Deploying synapse artifacts of " + gatewayAPIDTO.getName());
                                apiGatewayAdmin.deployAPI(gatewayAPIDTO);
                                addDeployedCertificatesToAPIAssociation(gatewayAPIDTO);
                            }
                        } catch (AxisFault axisFault) {
                            log.error("Error in deploying " + gatewayAPIDTO.getName() + " to the Gateway ", axisFault);
                        }
                    }

                    if (debugEnabled) {
                        log.debug("APIs deployed in gateway with the labels of " + labelString);
                    }
                    return true;
                } catch (ArtifactSynchronizerException | AxisFault e) {
                    String msg = "Error  deploying APIs to the Gateway ";
                    log.error(msg, e);
                    throw new ArtifactSynchronizerException(msg, e);
                } finally {
                    MessageContext.destroyCurrentMessageContext();
                    PrivilegedCarbonContext.endTenantFlow();
                }
            } else {
                String msg = "Artifact retriever not found";
                log.error(msg);
                throw new ArtifactSynchronizerException(msg);
            }
        }
        return false;
    }

    /**
     * UnDeploy an API in the gateway using the uneployAPI method in gateway admin
     *
     * @param apiId        - UUID of the API
     * @param gatewayLabel - Label of the Gateway
     * @return True if API artifact retrieved from the storage and successfully undeployed without any error. else false
     */
    public boolean unDeployAPI(String apiId, String gatewayLabel) throws ArtifactSynchronizerException {

        return false;
    }

    /**
     * Retrieve artifacts from the storage
     *
     * @param apiId        - UUID of the API
     * @param gatewayLabel - Label of the Gateway
     * @return DTO Object that contains the information and artifacts of the API for the given label
     */
    public GatewayAPIDTO getAPIArtifact(String apiId, String gatewayLabel) throws ArtifactSynchronizerException {

        GatewayAPIDTO gatewayAPIDTO = null;
        if (gatewayArtifactSynchronizerProperties.getGatewayLabels().contains(gatewayLabel)) {
            if (artifactRetriever != null) {
                String gatewayRuntimeArtifact = artifactRetriever.retrieveArtifact(apiId, gatewayLabel);
                if (StringUtils.isNotEmpty(gatewayRuntimeArtifact)) {
                    gatewayAPIDTO = new Gson().fromJson(gatewayRuntimeArtifact, GatewayAPIDTO.class);
                    if (debugEnabled) {
                        log.debug("Retrieved artifacts for API  " + apiId + " retrieved from eventhub");
                    }
                } else {
                    String msg = "Error retrieving artifacts for API " + apiId + ". Storage returned null";
                    log.error(msg);
                    throw new ArtifactSynchronizerException(msg);
                }
            } else {
                String msg = "Artifact retriever not found";
                log.error(msg);
                throw new ArtifactSynchronizerException(msg);
            }
        }
        return gatewayAPIDTO;
    }

    /**
     * Retrieve artifacts from the storage
     *
     * @param apiName      - Name of the API
     * @param version      - version of the API
     * @param tenantDomain - Tenant Domain of the API
     * @return Map that contains the UUID and label of the API
     */
    public Map<String, String> getGatewayAPIAttributes(String apiName, String version, String tenantDomain)
            throws ArtifactSynchronizerException {

        Map<String, String> apiAttributes = null;
        if (artifactRetriever != null) {
            try {
                apiAttributes = artifactRetriever.retrieveAttributes(apiName, version, tenantDomain);
                if (debugEnabled) {
                    log.debug("API Attributes retrieved for " + apiName + "  from storage");
                }
            } catch (ArtifactSynchronizerException e) {
                String msg = "Error retrieving artifacts of " + apiName + " from storage";
                log.error(msg, e);
                throw new ArtifactSynchronizerException(msg, e);
            }
        } else {
            String msg = "Artifact retriever not found";
            log.error(msg);
            throw new ArtifactSynchronizerException(msg);
        }
        return apiAttributes;
    }

    public void unDeployAPI(DeployAPIInGatewayEvent gatewayEvent) throws ArtifactSynchronizerException {

        try {
            if (gatewayArtifactSynchronizerProperties.isRetrieveFromStorageEnabled()) {
                APIGatewayAdmin apiGatewayAdmin = new APIGatewayAdmin();
                MessageContext.setCurrentMessageContext(org.wso2.carbon.apimgt.gateway.utils.GatewayUtils.createAxis2MessageContext());
                API api = new API(new APIIdentifier(gatewayEvent.getProvider(), gatewayEvent.getName(),
                        gatewayEvent.getVersion()));
                GatewayAPIDTO gatewayAPIDTO = new GatewayAPIDTO();
                gatewayAPIDTO.setName(gatewayEvent.getName());
                gatewayAPIDTO.setVersion(gatewayEvent.getVersion());
                gatewayAPIDTO.setProvider(gatewayEvent.getProvider());
                gatewayAPIDTO.setTenantDomain(gatewayEvent.getTenantDomain());
                gatewayAPIDTO.setOverride(true);
                gatewayAPIDTO.setApiId(gatewayEvent.getUuid());
                setClientCertificatesToRemoveIntoGatewayDTO(gatewayAPIDTO);
                if (APIConstants.API_PRODUCT.equals(gatewayEvent.getApiType())) {
                    gatewayAPIDTO.setOverride(false);
                    Set<APIEvent> associatedApis = gatewayEvent.getAssociatedApis();
                    for (APIEvent associatedApi : associatedApis) {
                        if (!APIStatus.PUBLISHED.getStatus().equals(associatedApi.getApiStatus())) {
                            GatewayUtils.setCustomSequencesToBeRemoved(
                                    new API(new APIIdentifier(associatedApi.getApiProvider(),
                                            associatedApi.getApiName(),
                                            associatedApi.getApiVersion())), gatewayAPIDTO);
                            GatewayUtils
                                    .setEndpointsToBeRemoved(associatedApi.getApiName(), associatedApi.getApiVersion(),
                                            gatewayAPIDTO);
                        }
                    }
                } else {
                    if (APIConstants.APITransportType.GRAPHQL.toString().equalsIgnoreCase(gatewayEvent.getApiType())) {
                        gatewayAPIDTO.setLocalEntriesToBeRemove(
                                org.wso2.carbon.apimgt.impl.utils.GatewayUtils
                                        .addStringToList(gatewayEvent.getUuid().concat(
                                                "_graphQL"), gatewayAPIDTO.getLocalEntriesToBeRemove()));
                    }
                    GatewayUtils.setEndpointsToBeRemoved(gatewayAPIDTO.getName(), gatewayAPIDTO.getVersion(),
                            gatewayAPIDTO);
                    GatewayUtils.setCustomSequencesToBeRemoved(api, gatewayAPIDTO);
                }
                gatewayAPIDTO.setLocalEntriesToBeRemove(
                        GatewayUtils
                                .addStringToList(gatewayEvent.getUuid(), gatewayAPIDTO.getLocalEntriesToBeRemove()));
                apiGatewayAdmin.unDeployAPI(gatewayAPIDTO);
                DataHolder.getInstance().getApiToCertificatesMap().remove(gatewayEvent.getUuid());
            }
        } catch (AxisFault axisFault) {
            throw new ArtifactSynchronizerException("Error while unDeploying api ", axisFault);
        } finally {
            MessageContext.destroyCurrentMessageContext();
        }
    }

    public void cleanDeployment(String artifactRepositoryPath) {

        File artifactRepoPath =
                Paths.get(artifactRepositoryPath, SynapseConstants.SYNAPSE_CONFIGS, SynapseConstants.DEFAULT_DIR)
                        .toFile();
        if (artifactRepoPath.exists() && artifactRepoPath.isDirectory()) {
            GatewayCleanupSkipList gatewayCleanupSkipList =
                    ServiceReferenceHolder.getInstance().getAPIManagerConfiguration().getGatewayCleanupSkipList();
            File apiPath = Paths.get(artifactRepoPath.getAbsolutePath(), "api").toFile();
            if (apiPath.exists() && apiPath.isDirectory()) {
                clean(apiPath, gatewayCleanupSkipList.getApis());
            }
            File localEntryPath = Paths.get(artifactRepoPath.getAbsolutePath(), "local-entries").toFile();
            if (localEntryPath.exists() && localEntryPath.isDirectory()) {
                clean(localEntryPath, gatewayCleanupSkipList.getLocalEntries());
            }
            File endpointPath = Paths.get(artifactRepoPath.getAbsolutePath(), "endpoints").toFile();
            if (endpointPath.exists() && endpointPath.isDirectory()) {
                clean(endpointPath, gatewayCleanupSkipList.getEndpoints());
            }
            File sequencesPath = Paths.get(artifactRepoPath.getAbsolutePath(), "sequences").toFile();
            if (sequencesPath.exists() && sequencesPath.isDirectory()) {
                clean(sequencesPath, gatewayCleanupSkipList.getSequences());
            }
        }
    }

    private void clean(File artifactRepoPath, Set<String> skippedList) {

        if (artifactRepoPath != null && artifactRepoPath.isDirectory()) {
            for (File file : Objects.requireNonNull(artifactRepoPath.listFiles())) {
                if (!skippedList.contains(file.getName())) {
                    file.delete();
                }
            }
        }
    }

    private void addDeployedCertificatesToAPIAssociation(GatewayAPIDTO gatewayAPIDTO) {

        if (gatewayAPIDTO != null) {
            String apiId = gatewayAPIDTO.getApiId();
            List<String> aliasList = new ArrayList<>();
            if (gatewayAPIDTO.getClientCertificatesToBeAdd() != null) {
                for (GatewayContentDTO gatewayContentDTO : gatewayAPIDTO.getClientCertificatesToBeAdd()) {
                    aliasList.add(gatewayContentDTO.getName());
                }
            }
            DataHolder.getInstance().addApiToAliasList(apiId, aliasList);
        }
    }

    private void setClientCertificatesToRemoveIntoGatewayDTO(GatewayAPIDTO gatewayDTO) {

        if (gatewayDTO != null) {
            if (StringUtils.isNotEmpty(gatewayDTO.getApiId())) {
                List<String> certificateAliasListForAPI =
                        DataHolder.getInstance().getCertificateAliasListForAPI(gatewayDTO.getApiId());
                gatewayDTO.setClientCertificatesToBeRemove(certificateAliasListForAPI.toArray(new String[0]));
            }
        }
    }

    public void deployAPI(DeployAPIInGatewayEvent gatewayEvent)
            throws ArtifactSynchronizerException {

        unDeployAPI(gatewayEvent);
        deployAPI(gatewayEvent.getUuid(), gatewayEvent.getGatewayLabels());
    }

    public void reDeployAPI(String apiName, String version, String tenantDomain) throws ArtifactSynchronizerException {

        SubscriptionDataStore tenantSubscriptionStore =
                SubscriptionDataHolder.getInstance().getTenantSubscriptionStore(tenantDomain);
        Set<String> gatewayLabels = gatewayArtifactSynchronizerProperties.getGatewayLabels();
        if (tenantSubscriptionStore != null) {
            org.wso2.carbon.apimgt.keymgt.model.entity.API retrievedAPI =
                    tenantSubscriptionStore.getApiByNameAndVersion(apiName, version);
            if (retrievedAPI != null) {
                DeployAPIInGatewayEvent deployAPIInGatewayEvent =
                        new DeployAPIInGatewayEvent(UUID.randomUUID().toString(), System.currentTimeMillis(),
                                APIConstants.EventType.REMOVE_API_FROM_GATEWAY.name(), tenantDomain,
                                retrievedAPI.getApiId(), retrievedAPI.getUuid(), gatewayLabels, apiName, version,
                                retrievedAPI.getApiProvider(),
                                retrievedAPI.getApiType(), retrievedAPI.getContext());
                deployAPI(deployAPIInGatewayEvent);
            }
        }
    }

    public void unDeployAPI(String apiName, String version, String tenantDomain) throws ArtifactSynchronizerException {

        SubscriptionDataStore tenantSubscriptionStore =
                SubscriptionDataHolder.getInstance().getTenantSubscriptionStore(tenantDomain);
        Set<String> gatewayLabels = gatewayArtifactSynchronizerProperties.getGatewayLabels();
        if (tenantSubscriptionStore != null) {
            org.wso2.carbon.apimgt.keymgt.model.entity.API retrievedAPI =
                    tenantSubscriptionStore.getApiByNameAndVersion(apiName, version);
            if (retrievedAPI != null) {
                DeployAPIInGatewayEvent deployAPIInGatewayEvent =
                        new DeployAPIInGatewayEvent(UUID.randomUUID().toString(), System.currentTimeMillis(),
                                APIConstants.EventType.REMOVE_API_FROM_GATEWAY.name(), tenantDomain,
                                retrievedAPI.getApiId(), retrievedAPI.getUuid(), gatewayLabels, apiName, version,
                                retrievedAPI.getApiProvider(), retrievedAPI.getApiType(), retrievedAPI.getContext());
                unDeployAPI(deployAPIInGatewayEvent);
            }
        }
    }
}
