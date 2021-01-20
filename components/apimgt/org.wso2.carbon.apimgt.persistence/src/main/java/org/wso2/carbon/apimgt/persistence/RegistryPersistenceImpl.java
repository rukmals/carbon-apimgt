/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.apimgt.persistence;

import static org.wso2.carbon.apimgt.persistence.utils.PersistenceUtil.handleException;
import static org.wso2.carbon.apimgt.persistence.utils.RegistryPersistenceUtil.getLcStateFromArtifact;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.entity.ContentType;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtResourceNotFoundException;
import org.wso2.carbon.apimgt.api.ExceptionCodes;
import org.wso2.carbon.apimgt.api.model.*;
import org.wso2.carbon.apimgt.persistence.dto.DevPortalAPI;
import org.wso2.carbon.apimgt.persistence.dto.DevPortalAPIInfo;
import org.wso2.carbon.apimgt.persistence.dto.DevPortalAPISearchResult;
import org.wso2.carbon.apimgt.persistence.dto.DevPortalContentSearchResult;
import org.wso2.carbon.apimgt.persistence.dto.PublisherSearchContent;
import org.wso2.carbon.apimgt.persistence.dto.DocumentContent;
import org.wso2.carbon.apimgt.persistence.dto.DocumentContent.ContentSourceType;
import org.wso2.carbon.apimgt.persistence.dto.DocumentSearchContent;
import org.wso2.carbon.apimgt.persistence.dto.DocumentSearchResult;
import org.wso2.carbon.apimgt.persistence.dto.Documentation;
import org.wso2.carbon.apimgt.persistence.dto.Mediation;
import org.wso2.carbon.apimgt.persistence.dto.MediationInfo;
import org.wso2.carbon.apimgt.persistence.dto.Organization;
import org.wso2.carbon.apimgt.persistence.dto.PublisherAPI;
import org.wso2.carbon.apimgt.persistence.dto.PublisherAPIInfo;
import org.wso2.carbon.apimgt.persistence.dto.PublisherAPISearchResult;
import org.wso2.carbon.apimgt.persistence.dto.PublisherContentSearchResult;
import org.wso2.carbon.apimgt.persistence.dto.DevPortalSearchContent;
import org.wso2.carbon.apimgt.persistence.dto.ResourceFile;
import org.wso2.carbon.apimgt.persistence.dto.SearchContent;
import org.wso2.carbon.apimgt.persistence.dto.UserContext;
import org.wso2.carbon.apimgt.persistence.exceptions.APIPersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.DocumentationPersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.GraphQLPersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.MediationPolicyPersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.OASPersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.PersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.ThumbnailPersistenceException;
import org.wso2.carbon.apimgt.persistence.exceptions.WSDLPersistenceException;
import org.wso2.carbon.apimgt.persistence.internal.PersistenceManagerComponent;
import org.wso2.carbon.apimgt.persistence.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.persistence.mapper.APIMapper;
import org.wso2.carbon.apimgt.persistence.utils.RegistryPersistanceDocUtil;
import org.wso2.carbon.apimgt.persistence.utils.RegistryPersistenceUtil;
import org.wso2.carbon.apimgt.persistence.utils.RegistrySearchUtil;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.common.dataobjects.GovernanceArtifact;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.common.ResourceData;
import org.wso2.carbon.registry.common.utils.artifact.manager.ArtifactManager;
import org.wso2.carbon.registry.core.ActionConstants;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.CollectionImpl;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.pagination.PaginationContext;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.indexing.indexer.IndexerException;
import org.wso2.carbon.registry.indexing.service.ContentBasedSearchService;
import org.wso2.carbon.registry.indexing.service.SearchResultsBean;
import org.wso2.carbon.registry.indexing.solr.SolrClient;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

public class RegistryPersistenceImpl implements APIPersistence {

    private static final Log log = LogFactory.getLog(RegistryPersistenceImpl.class);
/*    private static APIPersistence instance;
    protected int tenantId = MultitenantConstants.INVALID_TENANT_ID; //-1 the issue does not occur.;
    protected Registry registry;
    protected String tenantDomain;
    protected UserRegistry configRegistry;
    protected String username;
    protected Organization organization;
    private RegistryService registryService;
    private GenericArtifactManager apiGenericArtifactManager;
  */  
    protected String username;
    public RegistryPersistenceImpl(String username) {
 /*       this.registryService = ServiceReferenceHolder.getInstance().getRegistryService();
        this.username = username;
        try {
            // is it ok to reuse artifactManager object TODO : resolve this concern
            // this.registry = getRegistryService().getGovernanceUserRegistry();


            if (username == null) {

                this.registry = getRegistryService().getGovernanceUserRegistry();
                this.configRegistry = getRegistryService().getConfigSystemRegistry();

                this.username = CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME;
                ServiceReferenceHolder.setUserRealm((ServiceReferenceHolder.getInstance().getRealmService()
                                                .getBootstrapRealm()));
                this.apiGenericArtifactManager = RegistryPersistenceUtil.getArtifactManager(this.registry,
                                                APIConstants.API_KEY);
            } else {
                String tenantDomainName = MultitenantUtils.getTenantDomain(username);
                String tenantUserName = getTenantAwareUsername(username);
                int tenantId = getTenantManager().getTenantId(tenantDomainName);
                this.tenantId = tenantId;
                this.tenantDomain = tenantDomainName;
                this.organization = new Organization(Integer.toString(tenantId), tenantDomain, "registry");
                this.username = tenantUserName;

                loadTenantRegistry(tenantId);

                this.registry = getRegistryService().getGovernanceUserRegistry(tenantUserName, tenantId);

                this.configRegistry = getRegistryService().getConfigSystemRegistry(tenantId);

                //load resources for each tenants.
                RegistryPersistenceUtil.loadloadTenantAPIRXT(tenantUserName, tenantId);
                RegistryPersistenceUtil.loadTenantAPIPolicy(tenantUserName, tenantId);

                // ===== Below  calls should be called at impls module
                //                //Check whether GatewayType is "Synapse" before attempting to load Custom-Sequences into registry
                //                APIManagerConfiguration configuration = getAPIManagerConfiguration();
                //
                //                String gatewayType = configuration.getFirstProperty(APIConstants.API_GATEWAY_TYPE);
                //
                //                if (APIConstants.API_GATEWAY_TYPE_SYNAPSE.equalsIgnoreCase(gatewayType)) {
                //                    APIUtil.writeDefinedSequencesToTenantRegistry(tenantId);
                //                }

                ServiceReferenceHolder.setUserRealm((UserRealm) (ServiceReferenceHolder.getInstance().
                                                getRealmService().getTenantUserRealm(tenantId)));
                this.apiGenericArtifactManager = RegistryPersistenceUtil.getArtifactManager(this.registry,
                                                APIConstants.API_KEY);
            }
        } catch (RegistryException e) { //TODO fix these

        } catch (UserStoreException e) {
            e.printStackTrace();
        } catch (APIManagementException e) {
            e.printStackTrace();
        } catch (APIPersistenceException e) {
            e.printStackTrace();
       } */
       this.username = username;
    }

    protected String getTenantAwareUsername(String username) {
        return MultitenantUtils.getTenantAwareUsername(username);
    }

    protected void loadTenantRegistry(int apiTenantId) throws RegistryException {
        TenantRegistryLoader tenantRegistryLoader = PersistenceManagerComponent.getTenantRegistryLoader();
        ServiceReferenceHolder.getInstance().getIndexLoaderService().loadTenantIndex(apiTenantId);
        tenantRegistryLoader.loadTenantRegistry(apiTenantId);
    }

    protected TenantManager getTenantManager() {
        return ServiceReferenceHolder.getInstance().getRealmService().getTenantManager();
    }

    protected RegistryService getRegistryService() {
        return ServiceReferenceHolder.getInstance().getRegistryService();
    }
    
    @Override
    public PublisherAPI addAPI(Organization org, PublisherAPI publisherAPI) throws APIPersistenceException {
        
        API api = APIMapper.INSTANCE.toApi(publisherAPI);
        boolean transactionCommitted = false;
        boolean tenantFlowStarted = false;
        Registry registry = null;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            registry = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted();
            registry.beginTransaction();
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry, APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Failed to retrieve artifact manager when creating API " + api.getId().getApiName();
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            GenericArtifact genericArtifact =
                    artifactManager.newGovernanceArtifact(new QName(api.getId().getApiName()));
            if (genericArtifact == null) {
                String errorMessage = "Generic artifact is null when creating API " + api.getId().getApiName();
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            GenericArtifact artifact = RegistryPersistenceUtil.createAPIArtifactContent(genericArtifact, api);
            artifactManager.addGenericArtifact(artifact);
            //Attach the API lifecycle
            artifact.attachLifecycle(APIConstants.API_LIFE_CYCLE);
            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
            String providerPath = RegistryPersistenceUtil.getAPIProviderPath(api.getId());
            //provider ------provides----> API
            registry.addAssociation(providerPath, artifactPath, APIConstants.PROVIDER_ASSOCIATION);
            Set<String> tagSet = api.getTags();
            if (tagSet != null) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }
            
            List<Label> candidateLabelsList = api.getGatewayLabels();
            if (candidateLabelsList != null) {
                for (Label label : candidateLabelsList) {
                    artifact.addAttribute(APIConstants.API_LABELS_GATEWAY_LABELS, label.getName());
                }
            }

            String apiStatus = api.getStatus();
            saveAPIStatus(registry, artifactPath, apiStatus);

            String visibleRolesList = api.getVisibleRoles();
            String[] visibleRoles = new String[0];
            if (visibleRolesList != null) {
                visibleRoles = visibleRolesList.split(",");
            }

            String publisherAccessControlRoles = api.getAccessControlRoles();
            updateRegistryResources(registry, artifactPath, publisherAccessControlRoles, api.getAccessControl(),
                                            api.getAdditionalProperties());
            RegistryPersistenceUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(),
                                            visibleRoles, artifactPath, registry);

            if (api.getSwaggerDefinition() != null) {
                String resourcePath = RegistryPersistenceUtil.getOpenAPIDefinitionFilePath(api.getId().getName(),
                        api.getId().getVersion(), api.getId().getProviderName());
                resourcePath = resourcePath + APIConstants.API_OAS_DEFINITION_RESOURCE_NAME;
                Resource resource;
                if (!registry.resourceExists(resourcePath)) {
                    resource = registry.newResource();
                } else {
                    resource = registry.get(resourcePath);
                }
                resource.setContent(api.getSwaggerDefinition());
                resource.setMediaType("application/json");
                registry.put(resourcePath, resource);
                //Need to set anonymous if the visibility is public
                RegistryPersistenceUtil.clearResourcePermissions(resourcePath, api.getId(),
                        ((UserRegistry) registry).getTenantId());
                RegistryPersistenceUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(),
                        visibleRoles, resourcePath);
            }
            
            //Set permissions to doc path
            String docLocation = RegistryPersistanceDocUtil.getDocumentPath(api.getId().getProviderName(),
                    api.getId().getApiName(), api.getId().getVersion());
            RegistryPersistenceUtil.clearResourcePermissions(docLocation, api.getId(),
                    ((UserRegistry) registry).getTenantId());
            RegistryPersistenceUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(),
                    visibleRoles, docLocation);
            
            registry.commitTransaction();
            api.setUuid(artifact.getId());
            transactionCommitted = true;

            if (log.isDebugEnabled()) {
                log.debug("API details successfully added to the registry. API Name: " + api.getId().getApiName()
                        + ", API Version : " + api.getId().getVersion() + ", API context : " + api.getContext());
            }
            api.setCreatedTime(String.valueOf(new Date().getTime()));// set current time as created time for returning api.
            PublisherAPI returnAPI = APIMapper.INSTANCE.toPublisherApi(api);
            if (log.isDebugEnabled()) {
                log.debug("Created API :" + returnAPI.toString());
            }
            return returnAPI;
        } catch (RegistryException e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                // Throwing an error here would mask the original exception
                log.error("Error while rolling back the transaction for API: " + api.getId().getApiName(), re);
            }
            throw new APIPersistenceException("Error while performing registry transaction operation", e);
        } catch (APIManagementException e) {
            throw new APIPersistenceException("Error while creating API", e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
            try {
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                throw new APIPersistenceException(
                        "Error while rolling back the transaction for API: " + api.getId().getApiName(), ex);
            }
        }
    }

    @Override
    public PublisherAPI updateAPI(Organization org, PublisherAPI publisherAPI) throws APIPersistenceException {
        API api = APIMapper.INSTANCE.toApi(publisherAPI);

        boolean transactionCommitted = false;
        boolean tenantFlowStarted = false;
        Registry registry = null;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            registry = holder.getRegistry();
            tenantFlowStarted  = holder.isTenantFlowStarted();
            
            registry.beginTransaction();
            String apiArtifactId = registry.get(RegistryPersistenceUtil.getAPIPath(api.getId())).getUUID();
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry, APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when updating API artifact ID " + api.getId();
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);

            boolean isSecured = Boolean.parseBoolean(
                    artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_SECURED));
            boolean isDigestSecured = Boolean.parseBoolean(
                    artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_AUTH_DIGEST));
            String userName = artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_USERNAME);
            String password = artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD);
   
            if (!isSecured && !isDigestSecured && userName != null) {
                api.setEndpointUTUsername(userName);
                api.setEndpointUTPassword(password);
            }

            String oldStatus = artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS);
            Resource apiResource = registry.get(artifact.getPath());
            String oldAccessControlRoles = api.getAccessControlRoles();
            if (apiResource != null) {
                oldAccessControlRoles = registry.get(artifact.getPath()).getProperty(APIConstants.PUBLISHER_ROLES);
            }
            GenericArtifact updateApiArtifact = RegistryPersistenceUtil.createAPIArtifactContent(artifact, api);
            String artifactPath = GovernanceUtils.getArtifactPath(registry, updateApiArtifact.getId());
            org.wso2.carbon.registry.core.Tag[] oldTags = registry.getTags(artifactPath);
            if (oldTags != null) {
                for (org.wso2.carbon.registry.core.Tag tag : oldTags) {
                    registry.removeTag(artifactPath, tag.getTagName());
                }
            }
            Set<String> tagSet = api.getTags();
            if (tagSet != null) {
                for (String tag : tagSet) {
                    registry.applyTag(artifactPath, tag);
                }
            }
            if (api.isDefaultVersion()) {
                updateApiArtifact.setAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION, "true");
            } else {
                updateApiArtifact.setAttribute(APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION, "false");
            }


            artifactManager.updateGenericArtifact(updateApiArtifact);

            //write API Status to a separate property. This is done to support querying APIs using custom query (SQL)
            //to gain performance
            //String apiStatus = api.getStatus().toUpperCase();
            //saveAPIStatus(artifactPath, apiStatus);
            String[] visibleRoles = new String[0];
            String publisherAccessControlRoles = api.getAccessControlRoles();

            updateRegistryResources(registry, artifactPath, publisherAccessControlRoles, api.getAccessControl(),
                    api.getAdditionalProperties());

            //propagate api status change and access control roles change to document artifact
            String newStatus = updateApiArtifact.getAttribute(APIConstants.API_OVERVIEW_STATUS);
            if (!StringUtils.equals(oldStatus, newStatus) || !StringUtils.equals(oldAccessControlRoles, publisherAccessControlRoles)) {
                RegistryPersistenceUtil.notifyAPIStateChangeToAssociatedDocuments(artifact, registry);
            }

            // TODO Improve: add a visibility change check and only update if needed
            RegistryPersistenceUtil.clearResourcePermissions(artifactPath, api.getId(),
                    ((UserRegistry) registry).getTenantId());
            String visibleRolesList = api.getVisibleRoles();

            if (visibleRolesList != null) {
                visibleRoles = visibleRolesList.split(",");
            }
            RegistryPersistenceUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(),
                    visibleRoles, artifactPath, registry);
            
            //attaching api categories to the API
            List<APICategory> attachedApiCategories = api.getApiCategories();
            artifact.removeAttribute(APIConstants.API_CATEGORIES_CATEGORY_NAME);
            if (attachedApiCategories != null) {
                for (APICategory category : attachedApiCategories) {
                    artifact.addAttribute(APIConstants.API_CATEGORIES_CATEGORY_NAME, category.getName());
                }
            }
            
            if (api.getSwaggerDefinition() != null) {
                String resourcePath = RegistryPersistenceUtil.getOpenAPIDefinitionFilePath(api.getId().getName(),
                        api.getId().getVersion(), api.getId().getProviderName());
                resourcePath = resourcePath + APIConstants.API_OAS_DEFINITION_RESOURCE_NAME;
                Resource resource;
                if (!registry.resourceExists(resourcePath)) {
                    resource = registry.newResource();
                } else {
                    resource = registry.get(resourcePath);
                }
                resource.setContent(api.getSwaggerDefinition());
                resource.setMediaType("application/json");
                registry.put(resourcePath, resource);
                //Need to set anonymous if the visibility is public
                RegistryPersistenceUtil.clearResourcePermissions(resourcePath, api.getId(),
                        ((UserRegistry) registry).getTenantId());
                RegistryPersistenceUtil.setResourcePermissions(api.getId().getProviderName(), api.getVisibility(),
                        visibleRoles, resourcePath);
            }
            registry.commitTransaction();
            transactionCommitted = true;
            return APIMapper.INSTANCE.toPublisherApi(api);
        } catch (Exception e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                // Throwing an error from this level will mask the original exception
                log.error("Error while rolling back the transaction for API: " + api.getId().getApiName(), re);
            }
            throw new APIPersistenceException("Error while performing registry transaction operation ", e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
            try {
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                throw new APIPersistenceException("Error occurred while rolling back the transaction. ", ex);
            }
        }
    }
    
    

    /**
     * Things to populate manually -
     *      apistatus: check getAPIbyUUID() in abstractapimanager
     *      apiid: getAPIForPublishing(GovernanceArtifact artifact, Registry registry) in apiutil
     *      api.setRating ---
     *      api.setApiLevelPolicy --
     *       api.addAvailableTiers --
     *       api.setScopes --
     *       api.setUriTemplates --
     *       api.setEnvironments == read from config---
     *       api.setCorsConfiguration = if null get from configs
     *       api.setGatewayLabels == label name is set. other stuff seems not needed. if needed set them
     *      
     */
    @Override
    public PublisherAPI getPublisherAPI(Organization org, String apiId) throws APIPersistenceException {
        //test();
        boolean tenantFlowStarted = false;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            tenantFlowStarted  = holder.isTenantFlowStarted();
            Registry registry = holder.getRegistry();
            String requestedTenantDomain = org.getName();
            /*
            if (requestedTenantDomain  != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME
                                            .equals(requestedTenantDomain)) {
                int tenantId = getTenantManager().getTenantId(requestedTenantDomain);
                RegistryPersistenceUtil.startTenantFlow(requestedTenantDomain);
                tenantFlowStarted = true;
                registry = getRegistryService().getGovernanceSystemRegistry(tenantId);
            } else {
                if (this.tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME
                                                .equals(this.tenantDomain)) {
                    // at this point, requested tenant = carbon.super but logged in user is anonymous or tenant
                    registry = getRegistryService().getGovernanceSystemRegistry(MultitenantConstants.SUPER_TENANT_ID);
                } else {
                    // both requested tenant and logged in user's tenant are carbon.super
                    registry = this.registry;
                }
            }
            */
            
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                                            APIConstants.API_KEY);

            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiId);
            if (apiArtifact != null) {
                API api = RegistryPersistenceUtil.getApiForPublishing(registry, apiArtifact);
                api.setSwaggerDefinition(this.getOASDefinition(org, apiId));
                //TODO directly map to PublisherAPI from the registry
                PublisherAPI pubApi = APIMapper.INSTANCE.toPublisherApi(api) ; 
                if (log.isDebugEnabled()) {
                    log.debug("API for id " + apiId + " : " + pubApi.toString());
                }
                return pubApi;
            } else {
                String msg = "Failed to get API. API artifact corresponding to artifactId " + apiId + " does not exist";
                throw new APIMgtResourceNotFoundException(msg);
            }
        } catch (RegistryException e) {
            String msg = "Failed to get API";
            throw new APIPersistenceException(msg, e);
        } catch (APIManagementException e) {
            String msg = "Failed to get API";
            throw new APIPersistenceException(msg, e);
        } catch (OASPersistenceException e) {
            String msg = "Failed to retrieve OpenAPI definition for the API";
            throw new APIPersistenceException(msg, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
    }

    @Override
    public DevPortalAPI getDevPortalAPI(Organization org, String apiId) throws APIPersistenceException {
        //test(org.getName());
        boolean tenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted();
            String username = holder.getRegistryUser();
            //tenantFlowStarted = setRegistry(registry, org.getName());
            /*
            String requestedTenantDomain = org.getName();
            if (requestedTenantDomain  != null) {
                int id = getTenantManager().getTenantId(requestedTenantDomain);
                RegistryPersistenceUtil.startTenantFlow(requestedTenantDomain);
                tenantFlowStarted = true;
                if (APIConstants.WSO2_ANONYMOUS_USER.equals(this.username)) {
                    registry = getRegistryService().getGovernanceUserRegistry(this.username, id);
                } else if (this.tenantDomain != null && !this.tenantDomain.equals(requestedTenantDomain)) {
                    registry = getRegistryService().getGovernanceSystemRegistry(id);
                } else {
                    registry = this.registry;
                }
            } else {
                registry = this.registry;
            }
            */

            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiId);
            if (apiArtifact != null) {
                String type = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_TYPE);

                if (APIConstants.API_PRODUCT.equals(type)) {
                    /*
                    APIProduct apiProduct = getApiProduct(registry, apiArtifact);
                    String productTenantDomain = MultitenantUtils.getTenantDomain(
                            RegistryPersistenceUtil.replaceEmailDomainBack(apiProduct.getId().getProviderName()));
                    if (APIConstants.API_GLOBAL_VISIBILITY.equals(apiProduct.getVisibility())) {
                        return new ApiTypeWrapper(apiProduct);
                    }

                    if (this.tenantDomain == null || !this.tenantDomain.equals(productTenantDomain)) {
                        throw new APIManagementException(
                                "User " + username + " does not have permission to view API Product : " + apiProduct
                                        .getId().getName());
                    }

                    return new ApiTypeWrapper(apiProduct);
                    */
                    
                    //TODO previously there was a seperate method to get products. validate whether we could use api one instead
                    API api = RegistryPersistenceUtil.getApiForPublishing(registry, apiArtifact);
                    String apiTenantDomain = MultitenantUtils.getTenantDomain(
                            RegistryPersistenceUtil.replaceEmailDomainBack(api.getId().getProviderName()));
                    if (APIConstants.API_GLOBAL_VISIBILITY.equals(api.getVisibility())) {
                        return APIMapper.INSTANCE.toDevPortalApi(api);
                    }

                    if (tenantDomain == null || !tenantDomain.equals(apiTenantDomain)) {
                        throw new APIPersistenceException(
                                "User " + username + " does not have permission to view API : " + api.getId()
                                        .getApiName());
                    }
                    return APIMapper.INSTANCE.toDevPortalApi(api);
                } else {
                    API api = RegistryPersistenceUtil.getApiForPublishing(registry, apiArtifact);
                    String apiTenantDomain = MultitenantUtils.getTenantDomain(
                            RegistryPersistenceUtil.replaceEmailDomainBack(api.getId().getProviderName()));
                    if (APIConstants.API_GLOBAL_VISIBILITY.equals(api.getVisibility())) {
                        //return new ApiTypeWrapper(api);
                        return APIMapper.INSTANCE.toDevPortalApi(api);
                    }

                    if (tenantDomain == null || !tenantDomain.equals(apiTenantDomain)) {
                        throw new APIPersistenceException(
                                "User " + username + " does not have permission to view API : " + api.getId()
                                        .getApiName());
                    }

                    return APIMapper.INSTANCE.toDevPortalApi(api);
                }
            } else {
                return null;
            }
        } catch (RegistryException | APIManagementException e) {
            String msg = "Failed to get API";
            throw new APIPersistenceException(msg, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
    }

    @Override
    public void deleteAPI(Organization org, String apiId) throws APIPersistenceException {

        boolean transactionCommitted = false;
        boolean tenantFlowStarted = false;
        Registry registry = null;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            registry = holder.getRegistry();
            tenantFlowStarted  = holder.isTenantFlowStarted();
            registry.beginTransaction();
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Failed to retrieve artifact manager when deleting API " + apiId;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }

            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiId);
            APIIdentifier identifier = new APIIdentifier(apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER),
                    apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME),
                    apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));

            //Delete the dependencies associated  with the api artifact
            GovernanceArtifact[] dependenciesArray = apiArtifact.getDependencies();

            if (dependenciesArray.length > 0) {
                for (GovernanceArtifact artifact : dependenciesArray) {
                    registry.delete(artifact.getPath());
                }
            }
            
            artifactManager.removeGenericArtifact(apiArtifact);
            
            String path = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                    identifier.getProviderName() + RegistryConstants.PATH_SEPARATOR +
                    identifier.getApiName() + RegistryConstants.PATH_SEPARATOR + identifier.getVersion();
            Resource apiResource = registry.get(path);
            String artifactId = apiResource.getUUID();
            artifactManager.removeGenericArtifact(artifactId);

            String thumbPath = RegistryPersistenceUtil.getIconPath(identifier);
            if (registry.resourceExists(thumbPath)) {
                registry.delete(thumbPath);
            }

            String wsdlArchivePath = RegistryPersistenceUtil.getWsdlArchivePath(identifier);
            if (registry.resourceExists(wsdlArchivePath)) {
                registry.delete(wsdlArchivePath);
            }

            /*Remove API Definition Resource - swagger*/
            String apiDefinitionFilePath = APIConstants.API_DOC_LOCATION + RegistryConstants.PATH_SEPARATOR +
                    identifier.getApiName() + '-' + identifier.getVersion() + '-' + identifier.getProviderName();
            if (registry.resourceExists(apiDefinitionFilePath)) {
                registry.delete(apiDefinitionFilePath);
            }

            /*remove empty directories*/
            String apiCollectionPath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                    identifier.getProviderName() + RegistryConstants.PATH_SEPARATOR + identifier.getApiName();
            if (registry.resourceExists(apiCollectionPath)) {
                Resource apiCollection = registry.get(apiCollectionPath);
                CollectionImpl collection = (CollectionImpl) apiCollection;
                //if there is no other versions of apis delete the directory of the api
                if (collection.getChildCount() == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("No more versions of the API found, removing API collection from registry");
                    }
                    registry.delete(apiCollectionPath);
                }
            }

            String apiProviderPath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                    identifier.getProviderName();

            if (registry.resourceExists(apiProviderPath)) {
                Resource providerCollection = registry.get(apiProviderPath);
                CollectionImpl collection = (CollectionImpl) providerCollection;
                //if there is no api for given provider delete the provider directory
                if (collection.getChildCount() == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("No more APIs from the provider " + identifier.getProviderName() + " found. " +
                                "Removing provider collection from registry");
                    }
                    registry.delete(apiProviderPath);
                }
            }
            registry.commitTransaction();
            transactionCommitted  = true;
        } catch (RegistryException e) {
            throw new APIPersistenceException("Failed to remove the API : " + apiId, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
            try {
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                throw new APIPersistenceException("Error occurred while rolling back the transaction. ", ex);
            }
        }
    }

    @Override
    public PublisherAPISearchResult searchAPIsForPublisher(Organization org, String searchQuery, int start, int offset,
            UserContext ctx) throws APIPersistenceException {
        String requestedTenantDomain = org.getName();
        
        boolean isTenantFlowStarted = false;
        PublisherAPISearchResult result = null;
        try {
            RegistryHolder holder = getRegistry(requestedTenantDomain);
            Registry userRegistry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            int tenantIDLocal = holder.getTenantId();
            log.debug("Requested query for publisher search: " + searchQuery);
            
            String modifiedQuery = RegistrySearchUtil.getPublisherSearchQuery(searchQuery, ctx);
            
            log.debug("Modified query for publisher search: " + modifiedQuery);
            

            /*
            boolean isTenantMode = (requestedTenantDomain != null);
            if (isTenantMode && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(requestedTenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            } else {
                requestedTenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            }

            String userNameLocal = this.username;
            if ((isTenantMode && this.tenantDomain == null)
                    || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {// Tenant store anonymous
                                                                                            // mode
                tenantIDLocal = getTenantManager().getTenantId(requestedTenantDomain);
                RegistryPersistenceUtil.loadTenantRegistry(tenantIDLocal);///////////////////////////////////////////////////////////////////////
                userRegistry = getRegistryService()
                        .getGovernanceUserRegistry(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantIDLocal);
                userNameLocal = CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME;
                if (!requestedTenantDomain.equals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                    RegistryPersistenceUtil.loadTenantConfigBlockingMode(requestedTenantDomain);////////////////////////////////////////////////////////////////
                }
            } else {
                userRegistry = this.registry;
                tenantIDLocal = tenantId;
            }*/
            String userNameLocal = getTenantAwareUsername(holder.getRegistryUser());
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(holder.getRegistryUser());

            if (searchQuery != null && searchQuery.startsWith(APIConstants.DOCUMENTATION_SEARCH_TYPE_PREFIX)) {
                result = searchPaginatedPublisherAPIsByDoc(userRegistry, tenantIDLocal, searchQuery.split(":")[1],
                        userNameLocal, start, offset);
            } else if (searchQuery != null && searchQuery.startsWith(APIConstants.SUBCONTEXT_SEARCH_TYPE_PREFIX)) {
                result = searchPaginatedPublisherAPIsByURLPattern(userRegistry, tenantIDLocal,
                        searchQuery.split(":")[1], userNameLocal, start, offset);
            } else {
                result = searchPaginatedPublisherAPIs(userRegistry, tenantIDLocal, modifiedQuery, start, offset);
            }
        } catch (APIManagementException e) {
            throw new APIPersistenceException("Error while searching APIs " , e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return result;
    }

    private PublisherAPISearchResult searchPaginatedPublisherAPIs(Registry userRegistry, int tenantIDLocal, String searchQuery,
            int start, int offset) throws APIManagementException {
        int totalLength = 0;
        boolean isMore = false;
        PublisherAPISearchResult searchResults = new PublisherAPISearchResult();
        try {

            final int maxPaginationLimit = getMaxPaginationLimit();

            PaginationContext.init(start, offset, "ASC", APIConstants.API_OVERVIEW_NAME, maxPaginationLimit);

            List<GovernanceArtifact> governanceArtifacts = GovernanceUtils
                    .findGovernanceArtifacts(searchQuery, userRegistry, APIConstants.API_RXT_MEDIA_TYPE,
                            true);
            totalLength = PaginationContext.getInstance().getLength();
            boolean isFound = true;
            if (governanceArtifacts == null || governanceArtifacts.size() == 0) {
                if (searchQuery.contains(APIConstants.API_OVERVIEW_PROVIDER)) {
                    searchQuery = searchQuery.replaceAll(APIConstants.API_OVERVIEW_PROVIDER, APIConstants.API_OVERVIEW_OWNER);
                    governanceArtifacts = GovernanceUtils.findGovernanceArtifacts(searchQuery, userRegistry,
                            APIConstants.API_RXT_MEDIA_TYPE, true);
                    if (governanceArtifacts == null || governanceArtifacts.size() == 0) {
                        isFound = false;
                    }
                } else {
                    isFound = false;
                }
            }

            if (!isFound) {
                return searchResults;
            }

            // Check to see if we can speculate that there are more APIs to be loaded
            if (maxPaginationLimit == totalLength) {
                isMore = true;  // More APIs exist, cannot determine total API count without incurring perf hit
                --totalLength; // Remove the additional 1 added earlier when setting max pagination limit
            }
            List<PublisherAPIInfo> publisherAPIInfoList = new ArrayList<PublisherAPIInfo>();
            int tempLength = 0;
            for (GovernanceArtifact artifact : governanceArtifacts) {

                PublisherAPIInfo apiInfo = new PublisherAPIInfo();
                apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                apiInfo.setId(artifact.getId());
                apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                apiInfo.setStatus(artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS));
                apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                publisherAPIInfoList.add(apiInfo);

                // Ensure the APIs returned matches the length, there could be an additional API
                // returned due incrementing the pagination limit when getting from registry
                tempLength++;
                if (tempLength >= totalLength) {
                    break;
                }
            }

            searchResults.setPublisherAPIInfoList(publisherAPIInfoList);
            searchResults.setReturnedAPIsCount(publisherAPIInfoList.size());
            searchResults.setTotalAPIsCount(totalLength);
        } catch (RegistryException e) {
            String msg = "Failed to search APIs with type";
            throw new APIManagementException(msg, e);
        } finally {
            PaginationContext.destroy();
        }

        return searchResults;
    }

    @Override
    public DevPortalAPISearchResult searchAPIsForDevPortal(Organization org, String searchQuery, int start, int offset,
            UserContext ctx) throws APIPersistenceException {
        String requestedTenantDomain = org.getName();
        boolean isTenantMode = (requestedTenantDomain != null);
        boolean isTenantFlowStarted = false;
        DevPortalAPISearchResult result = null;
        try {
            RegistryHolder holder = getRegistry(requestedTenantDomain);
            Registry userRegistry = holder.getRegistry();
            int tenantIDLocal = holder.getTenantId();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            log.debug("Requested query for devportal search: " + searchQuery);
            String modifiedQuery = RegistrySearchUtil.getDevPortalSearchQuery(searchQuery, ctx);
            log.debug("Modified query for devportal search: " + modifiedQuery);
            

            /*
            if (isTenantMode && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(requestedTenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(requestedTenantDomain, true);
            } else {
                requestedTenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(requestedTenantDomain, true);
            }
            
            String userNameLocal = this.username;
            if ((isTenantMode && this.tenantDomain == null)
                    || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {// Tenant store anonymous
                                                                                            // mode
                tenantIDLocal = getTenantManager().getTenantId(requestedTenantDomain);
                RegistryPersistenceUtil.loadTenantRegistry(tenantIDLocal); //////////////////////////////////////////////////////////////////////
                userRegistry = getRegistryService()
                        .getGovernanceUserRegistry(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantIDLocal);
                userNameLocal = CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME;
                if (!requestedTenantDomain.equals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                    RegistryPersistenceUtil.loadTenantConfigBlockingMode(requestedTenantDomain);//////////////////////////////////////////////////////////////////////
                }
            } else {
                userRegistry = this.registry;
                tenantIDLocal = tenantId;
            } */
            
            String userNameLocal = getTenantAwareUsername(holder.getRegistryUser());
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(userNameLocal);

            if (searchQuery != null && searchQuery.startsWith(APIConstants.DOCUMENTATION_SEARCH_TYPE_PREFIX)) {
                result = searchPaginatedDevPortalAPIsByDoc(userRegistry, tenantIDLocal, searchQuery.split(":")[1],
                        userNameLocal, start, offset);
            } else if (searchQuery != null && searchQuery.startsWith(APIConstants.SUBCONTEXT_SEARCH_TYPE_PREFIX)) {
                result = searchPaginatedDevPortalAPIsByURLPattern(userRegistry, tenantIDLocal,
                        searchQuery.split(":")[1], userNameLocal, start, offset);
            } else {
                result = searchPaginatedDevPortalAPIs(userRegistry, tenantIDLocal, modifiedQuery, start, offset);
            }
        } catch (APIManagementException e) {
            throw new APIPersistenceException("Error while searching APIs " , e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return result;
    }

    private DevPortalAPISearchResult searchPaginatedDevPortalAPIs(Registry userRegistry, int tenantIDLocal,
            String searchQuery, int start, int offset) throws APIManagementException {
        int totalLength = 0;
        boolean isMore = false;
        DevPortalAPISearchResult searchResults = new DevPortalAPISearchResult();
        try {

            final int maxPaginationLimit = getMaxPaginationLimit();

            PaginationContext.init(start, offset, "ASC", APIConstants.API_OVERVIEW_NAME, maxPaginationLimit);
            log.debug("Dev portal list apis query " + searchQuery);
            List<GovernanceArtifact> governanceArtifacts = GovernanceUtils
                    .findGovernanceArtifacts(searchQuery, userRegistry, APIConstants.API_RXT_MEDIA_TYPE,
                            true);
            totalLength = PaginationContext.getInstance().getLength();
            boolean isFound = true;
            if (governanceArtifacts == null || governanceArtifacts.size() == 0) {
                if (searchQuery.contains(APIConstants.API_OVERVIEW_PROVIDER)) {
                    searchQuery = searchQuery.replaceAll(APIConstants.API_OVERVIEW_PROVIDER, APIConstants.API_OVERVIEW_OWNER);
                    governanceArtifacts = GovernanceUtils.findGovernanceArtifacts(searchQuery, userRegistry,
                            APIConstants.API_RXT_MEDIA_TYPE, true);
                    if (governanceArtifacts == null || governanceArtifacts.size() == 0) {
                        isFound = false;
                    }
                } else {
                    isFound = false;
                }
            }

            if (!isFound) {
                return searchResults;
            }

            // Check to see if we can speculate that there are more APIs to be loaded
            if (maxPaginationLimit == totalLength) {
                isMore = true;  // More APIs exist, cannot determine total API count without incurring perf hit
                --totalLength; // Remove the additional 1 added earlier when setting max pagination limit
            }
            List<DevPortalAPIInfo> devPortalAPIInfoList = new ArrayList<DevPortalAPIInfo>();

            List<DevPortalAPI> devPortalAPIList = new ArrayList<>();
            int tempLength = 0;
            for (GovernanceArtifact artifact : governanceArtifacts) {

                DevPortalAPIInfo apiInfo = new DevPortalAPIInfo();

                DevPortalAPI devPortalAPI = new DevPortalAPI();

                devPortalAPI.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                devPortalAPI.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                devPortalAPI.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));

                devPortalAPI.setId(artifact.getId());

                devPortalAPI.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));

                devPortalAPI.setStatus(getLcStateFromArtifact(artifact));
                devPortalAPI.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                devPortalAPI.setWsdlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WSDL));

                devPortalAPI.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));

                devPortalAPI.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));

                devPortalAPI.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
                devPortalAPI.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));

                devPortalAPI.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));

                devPortalAPI.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
                devPortalAPI.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));
                devPortalAPI.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));

                String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
                Set<String> availableTiers = new HashSet<>();
                if(tiers != null) {
                    String[] tiersArray = tiers.split("\\|\\|");
                    for(String tierName : tiersArray) {
                        availableTiers.add(tierName);
                    }
                }
                devPortalAPI.setAvailableTierNames(availableTiers);

                devPortalAPI.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));

                devPortalAPI.setMonetizationEnabled(Boolean.parseBoolean(artifact.getAttribute
                        (APIConstants.Monetization.API_MONETIZATION_STATUS)));
                Set<String> labels = new HashSet<>(Arrays.asList(artifact.getAttributes(APIConstants.API_LABELS_GATEWAY_LABELS)));
                devPortalAPI.setGatewayLabels(labels);

                //devPortalAPI.setApiCategories();
              // devPortalAPI.setEnvironments(getEnvironments(artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS)));
               devPortalAPI.setAuthorizationHeader(artifact.getAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER));
               devPortalAPI.setApiSecurity(artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY));

               devPortalAPIList.add(devPortalAPI);


                //devPortalAPIInfoList apiInfo = new devPortalAPIInfoList();
                apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                apiInfo.setId(artifact.getId());
                apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                apiInfo.setStatus(artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS));
                apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                apiInfo.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
                apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                devPortalAPIInfoList.add(apiInfo);

                // Ensure the APIs returned matches the length, there could be an additional API
                // returned due incrementing the pagination limit when getting from registry
                tempLength++;
                if (tempLength >= totalLength) {
                    break;
                }
            }

            searchResults.setDevPortalAPIList(devPortalAPIList);
            searchResults.setDevPortalAPIInfoList(devPortalAPIInfoList);
            searchResults.setReturnedAPIsCount(devPortalAPIInfoList.size());
            searchResults.setTotalAPIsCount(totalLength);
        } catch (RegistryException e) {
            String msg = "Failed to search APIs with type";
            throw new APIManagementException(msg, e);
        } finally {
            PaginationContext.destroy();
        }

        return searchResults;
    }

    private DevPortalAPISearchResult searchPaginatedDevPortalAPIsByDoc(Registry registry, int tenantID,
            String searchQuery, String username, int start, int offset) throws APIPersistenceException {
        int totalLength = 0;
        boolean isMore = false;
        DevPortalAPISearchResult searchResults = new DevPortalAPISearchResult();
        try {

            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(username);
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when searching APIs by docs in tenant ID " + tenantID;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            GenericArtifactManager docArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.DOCUMENTATION_KEY);
            if (docArtifactManager == null) {
                String errorMessage = "Doc artifact manager is null when searching APIs by docs in tenant ID " +
                        tenantID;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            SolrClient client = SolrClient.getInstance();
            Map<String, String> fields = new HashMap<String, String>();
            fields.put(APIConstants.DOCUMENTATION_SEARCH_PATH_FIELD, "*" + APIConstants.API_ROOT_LOCATION + "*");
            fields.put(APIConstants.DOCUMENTATION_SEARCH_MEDIA_TYPE_FIELD, "*");

            if (tenantID == -1) {
                tenantID = MultitenantConstants.SUPER_TENANT_ID;
            }
            //PaginationContext.init(0, 10000, "ASC", APIConstants.DOCUMENTATION_SEARCH_PATH_FIELD, Integer.MAX_VALUE);
            SolrDocumentList documentList = client.query(searchQuery, tenantID, fields);

            org.wso2.carbon.user.api.AuthorizationManager manager = ServiceReferenceHolder.getInstance().
                    getRealmService().getTenantUserRealm(tenantID).
                    getAuthorizationManager();

            username = MultitenantUtils.getTenantAwareUsername(username);
            List<DevPortalAPIInfo> devPortalAPIInfoList = new ArrayList<DevPortalAPIInfo>();
            for (SolrDocument document : documentList) {
                DevPortalAPIInfo apiInfo = new DevPortalAPIInfo();
                String filePath = (String) document.getFieldValue("path_s");
                String fileName = (String) document.getFieldValue("resourceName_s");
                int index = filePath.indexOf(APIConstants.APIMGT_REGISTRY_LOCATION);
                filePath = filePath.substring(index);
                boolean isAuthorized;
                int indexOfContents = filePath.indexOf(APIConstants.INLINE_DOCUMENT_CONTENT_DIR);
                String documentationPath = filePath.substring(0, indexOfContents) + fileName;
                String path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                        RegistryPersistenceUtil.getMountedPath(RegistryContext.getBaseInstance(),
                                RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + documentationPath);
                if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(username)) {
                    isAuthorized = manager.isRoleAuthorized(APIConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                } else {
                    isAuthorized = manager.isUserAuthorized(username, path, ActionConstants.GET);
                }
                if (isAuthorized) {
                    int indexOfDocumentation = filePath.indexOf(APIConstants.DOCUMENTATION_KEY);
                    String apiPath = documentationPath.substring(0, indexOfDocumentation) + APIConstants.API_KEY;
                    path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                            RegistryPersistenceUtil.getMountedPath(RegistryContext.getBaseInstance(),
                                    RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + apiPath);
                    if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(username)) {
                        isAuthorized = manager.isRoleAuthorized(APIConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                    } else {
                        isAuthorized = manager.isUserAuthorized(username, path, ActionConstants.GET);
                    }

                    if (isAuthorized) {
                        Resource resource = registry.get(apiPath);
                        String apiArtifactId = resource.getUUID();
                        if (apiArtifactId != null) {
                            GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);
                            String status = artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS);
                            if (APIConstants.PUBLISHED.equals(status) ||
                                    APIConstants.PROTOTYPED.equals(status)) {

                                apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                                apiInfo.setId(artifact.getId());
                                apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                                apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                                apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                                apiInfo.setStatus(status);
                                apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                                apiInfo.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
                                apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                                devPortalAPIInfoList.add(apiInfo);
                            }

                        } else {
                            throw new GovernanceException("artifact id is null of " + apiPath);
                        }
                    }
                }
            }
            searchResults.setDevPortalAPIInfoList(devPortalAPIInfoList);
            searchResults.setTotalAPIsCount(devPortalAPIInfoList.size());
            searchResults.setReturnedAPIsCount(devPortalAPIInfoList.size());
        } catch (RegistryException | UserStoreException | APIPersistenceException | IndexerException e) {
            String msg = "Failed to search APIs with type";
            throw new APIPersistenceException(msg, e);
        } finally {
            PaginationContext.destroy();
        }

        return searchResults;
    }

    private DevPortalAPISearchResult searchPaginatedDevPortalAPIsByURLPattern(Registry registry, int tenantID,
            String searchQuery, String username, int start, int end) throws APIPersistenceException {
        GenericArtifact[] genericArtifacts = new GenericArtifact[0];
        GenericArtifactManager artifactManager = null;
        int totalLength = 0;
        String criteria;
        DevPortalAPISearchResult searchResults = new DevPortalAPISearchResult();
        final String searchValue = searchQuery.trim();
        Map<String, List<String>> listMap = new HashMap<String, List<String>>();
        try {

            artifactManager = RegistryPersistenceUtil.getArtifactManager(registry, APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when searching APIs by URL pattern " + searchQuery;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            PaginationContext.init(0, 10000, "ASC", APIConstants.API_OVERVIEW_NAME, Integer.MAX_VALUE);
            if (artifactManager != null) {
                for (int i = 0; i < 20; i++) { //This need to fix in future.We don't have a way to get max value of
                    // "url_template" entry stores in registry,unless we search in each API
                    criteria = APIConstants.API_URI_PATTERN + i;
                    listMap.put(criteria, new ArrayList<String>() {
                        {
                            add(searchValue);
                        }
                    });
                    genericArtifacts = (GenericArtifact[]) ArrayUtils.addAll(genericArtifacts, artifactManager
                            .findGenericArtifacts(listMap));
                }
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    return null;
                }
                totalLength = genericArtifacts.length;
                StringBuilder apiNames = new StringBuilder();
                List<DevPortalAPIInfo> devPortalAPIInfoList = new ArrayList<DevPortalAPIInfo>();
                for (GenericArtifact artifact : genericArtifacts) {
                    if (artifact == null) {
                        log.error("Failed to retrieve an artifact when searching APIs by URL pattern : " + searchQuery +
                                " , continuing with next artifact.");
                        continue;
                    }
                    if (apiNames.indexOf(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME)) < 0) { // Not found 
                        
                        String status = getLcStateFromArtifact(artifact);
                        DevPortalAPIInfo apiInfo = new DevPortalAPIInfo();
                        if (isAllowDisplayAPIsWithMultipleStatus()) {
                            if (APIConstants.PUBLISHED.equals(status) || APIConstants.DEPRECATED.equals(status)
                                    || APIConstants.PROTOTYPED.equals(status)) {
                                apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                                apiInfo.setId(artifact.getId());
                                apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                                apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                                apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                                apiInfo.setStatus(status);
                                apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                                apiInfo.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
                                apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                                devPortalAPIInfoList.add(apiInfo);
                                apiNames.append(apiInfo.getApiName());
                            }
                        } else {
                            if (APIConstants.PUBLISHED.equals(status) || APIConstants.PROTOTYPED.equals(status)) {

                                apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                                apiInfo.setId(artifact.getId());
                                apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                                apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                                apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                                apiInfo.setStatus(status);
                                apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                                apiInfo.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
                                apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                                devPortalAPIInfoList.add(apiInfo);
                                apiNames.append(apiInfo.getApiName());
                            }
                        }
                    }
                    totalLength = devPortalAPIInfoList.size();
                }
                if (totalLength <= ((start + end) - 1)) {
                    end = totalLength;
                }
                searchResults.setDevPortalAPIInfoList(devPortalAPIInfoList);
                searchResults.setTotalAPIsCount(devPortalAPIInfoList.size());
                searchResults.setReturnedAPIsCount(devPortalAPIInfoList.size());
            }
        
        } catch (GovernanceException e) {
            throw new APIPersistenceException("Error while searching for subcontext ", e);
        }
        return searchResults;
        
    }
    
    private PublisherAPISearchResult searchPaginatedPublisherAPIsByDoc(Registry registry, int tenantID,
            String searchQuery, String username, int start, int offset) throws APIPersistenceException {
        int totalLength = 0;
        boolean isMore = false;
        PublisherAPISearchResult searchResults = new PublisherAPISearchResult();
        Map<Documentation, API> apiDocMap = new HashMap<Documentation, API>();
        try {

            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(username);
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when searching APIs by docs in tenant ID " + tenantID;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            GenericArtifactManager docArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.DOCUMENTATION_KEY);
            if (docArtifactManager == null) {
                String errorMessage = "Doc artifact manager is null when searching APIs by docs in tenant ID " +
                        tenantID;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            SolrClient client = SolrClient.getInstance();
            Map<String, String> fields = new HashMap<String, String>();
            fields.put(APIConstants.DOCUMENTATION_SEARCH_PATH_FIELD, "*" + APIConstants.API_ROOT_LOCATION + "*");
            fields.put(APIConstants.DOCUMENTATION_SEARCH_MEDIA_TYPE_FIELD, "*");

            if (tenantID == -1) {
                tenantID = MultitenantConstants.SUPER_TENANT_ID;
            }
            //PaginationContext.init(0, 10000, "ASC", APIConstants.DOCUMENTATION_SEARCH_PATH_FIELD, Integer.MAX_VALUE);
            SolrDocumentList documentList = client.query(searchQuery, tenantID, fields);

            org.wso2.carbon.user.api.AuthorizationManager manager = ServiceReferenceHolder.getInstance().
                    getRealmService().getTenantUserRealm(tenantID).
                    getAuthorizationManager();

            username = MultitenantUtils.getTenantAwareUsername(username);
            List<PublisherAPIInfo> publisherAPIInfoList = new ArrayList<PublisherAPIInfo>();
            for (SolrDocument document : documentList) {
                PublisherAPIInfo apiInfo = new PublisherAPIInfo();
                String filePath = (String) document.getFieldValue("path_s");
                String fileName = (String) document.getFieldValue("resourceName_s");
                int index = filePath.indexOf(APIConstants.APIMGT_REGISTRY_LOCATION);
                filePath = filePath.substring(index);
                boolean isAuthorized;
                int indexOfContents = filePath.indexOf(APIConstants.INLINE_DOCUMENT_CONTENT_DIR);
                String documentationPath = filePath.substring(0, indexOfContents) + fileName;
                String path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                        RegistryPersistenceUtil.getMountedPath(RegistryContext.getBaseInstance(),
                                RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + documentationPath);
                if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(username)) {
                    isAuthorized = manager.isRoleAuthorized(APIConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                } else {
                    isAuthorized = manager.isUserAuthorized(username, path, ActionConstants.GET);
                }
                if (isAuthorized) {
                    int indexOfDocumentation = filePath.indexOf(APIConstants.DOCUMENTATION_KEY);
                    String apiPath = documentationPath.substring(0, indexOfDocumentation) + APIConstants.API_KEY;
                    path = RegistryUtils.getAbsolutePath(RegistryContext.getBaseInstance(),
                            RegistryPersistenceUtil.getMountedPath(RegistryContext.getBaseInstance(),
                                    RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + apiPath);
                    if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equalsIgnoreCase(username)) {
                        isAuthorized = manager.isRoleAuthorized(APIConstants.ANONYMOUS_ROLE, path, ActionConstants.GET);
                    } else {
                        isAuthorized = manager.isUserAuthorized(username, path, ActionConstants.GET);
                    }

                    if (isAuthorized) {
                        Resource resource = registry.get(apiPath);
                        String apiArtifactId = resource.getUUID();
                        if (apiArtifactId != null) {
                            GenericArtifact artifact = artifactManager.getGenericArtifact(apiArtifactId);
                            String status = artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS);
                            if (APIConstants.PUBLISHED.equals(status) ||
                                    APIConstants.PROTOTYPED.equals(status)) {

                                apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                                apiInfo.setId(artifact.getId());
                                apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                                apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                                apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                                apiInfo.setStatus(status);
                                apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                                //apiInfo.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
                                apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                                publisherAPIInfoList.add(apiInfo);
                            }

                        } else {
                            throw new GovernanceException("artifact id is null of " + apiPath);
                        }
                    }
                }
            }
            searchResults.setPublisherAPIInfoList(publisherAPIInfoList);
            searchResults.setTotalAPIsCount(publisherAPIInfoList.size());
            searchResults.setReturnedAPIsCount(publisherAPIInfoList.size());
        } catch (RegistryException | UserStoreException | APIPersistenceException | IndexerException e) {
            String msg = "Failed to search APIs with type";
            throw new APIPersistenceException(msg, e);
        } finally {
            PaginationContext.destroy();
        }

        return searchResults;
    }

    private PublisherAPISearchResult searchPaginatedPublisherAPIsByURLPattern(Registry registry, int tenantID,
            String searchQuery, String username, int start, int end) throws APIPersistenceException {
        GenericArtifact[] genericArtifacts = new GenericArtifact[0];
        GenericArtifactManager artifactManager = null;
        int totalLength = 0;
        String criteria;
        PublisherAPISearchResult searchResults = new PublisherAPISearchResult();
        final String searchValue = searchQuery.trim();
        Map<String, List<String>> listMap = new HashMap<String, List<String>>();
        try {

            artifactManager = RegistryPersistenceUtil.getArtifactManager(registry, APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Artifact manager is null when searching APIs by URL pattern " + searchQuery;
                log.error(errorMessage);
                throw new APIPersistenceException(errorMessage);
            }
            PaginationContext.init(0, 10000, "ASC", APIConstants.API_OVERVIEW_NAME, Integer.MAX_VALUE);
            if (artifactManager != null) {
                for (int i = 0; i < 20; i++) { //This need to fix in future.We don't have a way to get max value of
                    // "url_template" entry stores in registry,unless we search in each API
                    criteria = APIConstants.API_URI_PATTERN + i;
                    listMap.put(criteria, new ArrayList<String>() {
                        {
                            add(searchValue);
                        }
                    });
                    genericArtifacts = (GenericArtifact[]) ArrayUtils.addAll(genericArtifacts, artifactManager
                            .findGenericArtifacts(listMap));
                }
                if (genericArtifacts == null || genericArtifacts.length == 0) {
                    return null;
                }
                totalLength = genericArtifacts.length;
                StringBuilder apiNames = new StringBuilder();
                List<PublisherAPIInfo> apiInfoList = new ArrayList<PublisherAPIInfo>();
                for (GenericArtifact artifact : genericArtifacts) {
                    if (artifact == null) {
                        log.error("Failed to retrieve an artifact when searching APIs by URL pattern : " + searchQuery +
                                " , continuing with next artifact.");
                        continue;
                    }
                    if (apiNames.indexOf(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME)) < 0) { // Not found 
                        
                        String status = getLcStateFromArtifact(artifact);
                        PublisherAPIInfo apiInfo = new PublisherAPIInfo();
                        apiInfo.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
                        apiInfo.setId(artifact.getId());
                        apiInfo.setApiName(artifact.getAttribute(APIConstants.API_OVERVIEW_NAME));
                        apiInfo.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
                        apiInfo.setProviderName(artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER));
                        apiInfo.setStatus(status);
                        apiInfo.setThumbnail(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
                        //apiInfo.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
                        apiInfo.setVersion(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                        apiInfoList.add(apiInfo);
                        apiNames.append(apiInfo.getApiName());
                    }
                    totalLength = apiInfoList.size();
                }
                if (totalLength <= ((start + end) - 1)) {
                    end = totalLength;
                }
                searchResults.setPublisherAPIInfoList(apiInfoList);
                searchResults.setTotalAPIsCount(apiInfoList.size());
                searchResults.setReturnedAPIsCount(apiInfoList.size());
            }
        
        } catch (GovernanceException e) {
            throw new APIPersistenceException("Error while searching for subcontext ", e);
        }
        return searchResults;
        
    }

    private boolean isAllowDisplayAPIsWithMultipleStatus() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public PublisherContentSearchResult searchContentForPublisher(Organization org, String searchQuery, int start,
            int offset, UserContext ctx) throws APIPersistenceException {
        log.debug("Requested query for publisher content search: " + searchQuery);
        Map<String, String> attributes = RegistrySearchUtil.getPublisherSearchAttributes(searchQuery, ctx);
        if(log.isDebugEnabled()) {
            log.debug("Search attributes : " + attributes );
        }
        boolean isTenantFlowStarted = false;
        PublisherContentSearchResult result = null;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(holder.getRegistryUser());
            
            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            GenericArtifactManager docArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.DOCUMENTATION_KEY);
            int maxPaginationLimit = getMaxPaginationLimit();
            PaginationContext.init(start, offset, "ASC", APIConstants.API_OVERVIEW_NAME, maxPaginationLimit);

            int tenantId = holder.getTenantId();
            if (tenantId == -1) {
                tenantId = MultitenantConstants.SUPER_TENANT_ID;
            }

            UserRegistry systemUserRegistry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getRegistry(CarbonConstants.REGISTRY_SYSTEM_USERNAME, tenantId);
            ContentBasedSearchService contentBasedSearchService = new ContentBasedSearchService();
            
            SearchResultsBean resultsBean = contentBasedSearchService.searchByAttribute(attributes, systemUserRegistry);
            String errorMsg = resultsBean.getErrorMessage();
            if (errorMsg != null) {
                throw new APIPersistenceException("Error while searching " + errorMsg);
            }
            ResourceData[] resourceData = resultsBean.getResourceDataList();
            int totalLength = PaginationContext.getInstance().getLength();
            
            if(resourceData != null) {
                result = new PublisherContentSearchResult();
                List<SearchContent> contentData = new ArrayList<SearchContent>();
                if(log.isDebugEnabled()) {
                    log.debug("Number of records Found: " + resourceData.length);
                }

                
                
                for (ResourceData data : resourceData) {

                    String resourcePath = data.getResourcePath();
                    if (resourcePath.contains(APIConstants.APIMGT_REGISTRY_LOCATION)) {
                        int index = resourcePath.indexOf(APIConstants.APIMGT_REGISTRY_LOCATION);
                        resourcePath = resourcePath.substring(index);
                        Resource resource = registry.get(resourcePath);
                        if (APIConstants.DOCUMENT_RXT_MEDIA_TYPE.equals(resource.getMediaType()) ||
                                APIConstants.DOCUMENTATION_INLINE_CONTENT_TYPE.equals(resource.getMediaType())) {
                            if (resourcePath.contains(APIConstants.INLINE_DOCUMENT_CONTENT_DIR)) {
                                int indexOfContents = resourcePath.indexOf(APIConstants.INLINE_DOCUMENT_CONTENT_DIR);
                                resourcePath = resourcePath.substring(0, indexOfContents) + data.getName();
                            }
                            DocumentSearchContent docSearch = new DocumentSearchContent();
                            Resource docResource = registry.get(resourcePath);
                            String docArtifactId = docResource.getUUID();
                            GenericArtifact docArtifact = docArtifactManager.getGenericArtifact(docArtifactId);
                            Documentation doc = RegistryPersistanceDocUtil.getDocumentation(docArtifact);
                            //API associatedAPI = null;
                            //APIProduct associatedAPIProduct = null;
                            int indexOfDocumentation = resourcePath.indexOf(APIConstants.DOCUMENTATION_KEY);
                            String apiPath = resourcePath.substring(0, indexOfDocumentation) + APIConstants.API_KEY;
                            Resource apiResource = registry.get(apiPath);
                            String apiArtifactId = apiResource.getUUID();
                            PublisherAPI pubAPI;
                            if (apiArtifactId != null) {
                                GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiArtifactId);
                                String accociatedType;
                                if (apiArtifact.getAttribute(APIConstants.API_OVERVIEW_TYPE).
                                        equals(APIConstants.AuditLogConstants.API_PRODUCT)) {
                                    //associatedAPIProduct = APIUtil.getAPIProduct(apiArtifact, registry);
                                    accociatedType = APIConstants.API_PRODUCT;
                                } else {
                                    //associatedAPI = APIUtil.getAPI(apiArtifact, registry);
                                    accociatedType = APIConstants.API;
                                }
                                pubAPI = RegistryPersistenceUtil.getAPIForSearch(apiArtifact);
                                docSearch.setApiName(pubAPI.getApiName());
                                docSearch.setApiProvider(pubAPI.getProviderName());
                                docSearch.setApiVersion(pubAPI.getVersion());
                                docSearch.setApiUUID(pubAPI.getId());
                                docSearch.setAssociatedType(accociatedType);
                                docSearch.setDocType(doc.getType());
                                docSearch.setId(doc.getId());
                                docSearch.setSourceType(doc.getSourceType());
                                docSearch.setVisibility(doc.getVisibility());
                                docSearch.setName(doc.getName());
                                contentData.add(docSearch);
                            } else {
                                throw new GovernanceException("artifact id is null of " + apiPath);
                            }
                            
                        } else {
                            String apiArtifactId = resource.getUUID();
                            //API api;
                            //APIProduct apiProduct;
                            String type;
                            if (apiArtifactId != null) {
                                GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiArtifactId);
                                if (apiArtifact.getAttribute(APIConstants.API_OVERVIEW_TYPE).
                                        equals(APIConstants.API_PRODUCT)) {
                                    //apiProduct = APIUtil.getAPIProduct(apiArtifact, registry);
                                    //apiProductSet.add(apiProduct);
                                    type = APIConstants.API_PRODUCT;
                                } else {
                                    //api = APIUtil.getAPI(apiArtifact, registry);
                                    //apiSet.add(api);
                                    type = APIConstants.API;
                                }
                                PublisherAPI pubAPI = RegistryPersistenceUtil.getAPIForSearch(apiArtifact);
                                PublisherSearchContent content = new PublisherSearchContent();
                                content.setContext(pubAPI.getContext());
                                content.setDescription(pubAPI.getDescription());
                                content.setId(pubAPI.getId());
                                content.setName(pubAPI.getApiName());
                                content.setProvider(
                                        RegistryPersistenceUtil.replaceEmailDomainBack(pubAPI.getProviderName()));
                                content.setType(type);
                                content.setVersion(pubAPI.getVersion());
                                content.setStatus(pubAPI.getStatus());
                                contentData.add(content);
                            } else {
                                throw new GovernanceException("artifact id is null for " + resourcePath);
                            }
                        }
                    }
                
                }
                result.setTotalCount(totalLength);
                result.setReturnedCount(contentData.size());
                result.setResults(contentData);
            } 

        } catch (RegistryException | IndexerException | DocumentationPersistenceException e) {
            throw new APIPersistenceException("Error while searching for content ", e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return result;
    }

    @Override
    public DevPortalContentSearchResult searchContentForDevPortal(Organization org, String searchQuery, int start,
            int offset, UserContext ctx) throws APIPersistenceException {
        log.debug("Requested query for devportal content search: " + searchQuery);
        Map<String, String> attributes = RegistrySearchUtil.getDevPortalSearchAttributes(searchQuery, ctx);

        if(log.isDebugEnabled()) {
            log.debug("Search attributes : " + attributes );
        }
        DevPortalContentSearchResult result = null;
        boolean isTenantFlowStarted = false;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(holder.getRegistryUser());
            
            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            GenericArtifactManager docArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.DOCUMENTATION_KEY);
            int maxPaginationLimit = getMaxPaginationLimit();
            PaginationContext.init(start, offset, "ASC", APIConstants.API_OVERVIEW_NAME, maxPaginationLimit);

            int tenantId = holder.getTenantId();
            if (tenantId == -1) {
                tenantId = MultitenantConstants.SUPER_TENANT_ID;
            }

            UserRegistry systemUserRegistry = ServiceReferenceHolder.getInstance().getRegistryService()
                    .getRegistry(CarbonConstants.REGISTRY_SYSTEM_USERNAME, tenantId);
            ContentBasedSearchService contentBasedSearchService = new ContentBasedSearchService();
            
            SearchResultsBean resultsBean = contentBasedSearchService.searchByAttribute(attributes, systemUserRegistry);
            String errorMsg = resultsBean.getErrorMessage();
            if (errorMsg != null) {
                throw new APIPersistenceException("Error while searching " + errorMsg);
            }
            ResourceData[] resourceData = resultsBean.getResourceDataList();
            int totalLength = PaginationContext.getInstance().getLength();
            
            if(resourceData != null) {
                result = new DevPortalContentSearchResult();
                List<SearchContent> contentData = new ArrayList<SearchContent>();
                if(log.isDebugEnabled()) {
                    log.debug("Number of records Found: " + resourceData.length);
                }

                
                
                for (ResourceData data : resourceData) {

                    String resourcePath = data.getResourcePath();
                    if (resourcePath.contains(APIConstants.APIMGT_REGISTRY_LOCATION)) {
                        int index = resourcePath.indexOf(APIConstants.APIMGT_REGISTRY_LOCATION);
                        resourcePath = resourcePath.substring(index);
                        Resource resource = registry.get(resourcePath);
                        if (APIConstants.DOCUMENT_RXT_MEDIA_TYPE.equals(resource.getMediaType()) ||
                                APIConstants.DOCUMENTATION_INLINE_CONTENT_TYPE.equals(resource.getMediaType())) {
                            if (resourcePath.contains(APIConstants.INLINE_DOCUMENT_CONTENT_DIR)) {
                                int indexOfContents = resourcePath.indexOf(APIConstants.INLINE_DOCUMENT_CONTENT_DIR);
                                resourcePath = resourcePath.substring(0, indexOfContents) + data.getName();
                            }
                            DocumentSearchContent docSearch = new DocumentSearchContent();
                            Resource docResource = registry.get(resourcePath);
                            String docArtifactId = docResource.getUUID();
                            GenericArtifact docArtifact = docArtifactManager.getGenericArtifact(docArtifactId);
                            Documentation doc = RegistryPersistanceDocUtil.getDocumentation(docArtifact);
                            int indexOfDocumentation = resourcePath.indexOf(APIConstants.DOCUMENTATION_KEY);
                            String apiPath = resourcePath.substring(0, indexOfDocumentation) + APIConstants.API_KEY;
                            Resource apiResource = registry.get(apiPath);
                            String apiArtifactId = apiResource.getUUID();
                            DevPortalAPI devAPI;
                            if (apiArtifactId != null) {
                                GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiArtifactId);
                                devAPI = RegistryPersistenceUtil.getDevPortalAPIForSearch(apiArtifact);
                                docSearch.setApiName(devAPI.getApiName());
                                docSearch.setApiProvider(devAPI.getProviderName());
                                docSearch.setApiVersion(devAPI.getVersion());
                                docSearch.setApiUUID(devAPI.getId());
                                docSearch.setDocType(doc.getType());
                                docSearch.setId(doc.getId());
                                docSearch.setSourceType(doc.getSourceType());
                                docSearch.setVisibility(doc.getVisibility());
                                docSearch.setName(doc.getName());
                                contentData.add(docSearch);
                            } else {
                                throw new GovernanceException("artifact id is null of " + apiPath);
                            }
                            
                        } else {
                            String apiArtifactId = resource.getUUID();
                            if (apiArtifactId != null) {
                                GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiArtifactId);
                                DevPortalAPI devAPI = RegistryPersistenceUtil.getDevPortalAPIForSearch(apiArtifact);
                                DevPortalSearchContent content = new DevPortalSearchContent();
                                content.setContext(devAPI.getContext());
                                content.setDescription(devAPI.getDescription());
                                content.setId(devAPI.getId());
                                content.setName(devAPI.getApiName());
                                content.setProvider(
                                        RegistryPersistenceUtil.replaceEmailDomainBack(devAPI.getProviderName()));
                                content.setVersion(devAPI.getVersion());
                                content.setStatus(devAPI.getStatus());
                                content.setBusinessOwner(devAPI.getBusinessOwner());
                                content.setBusinessOwnerEmail(devAPI.getBusinessOwnerEmail());
                                
                                contentData.add(content);
                            } else {
                                throw new GovernanceException("artifact id is null for " + resourcePath);
                            }
                        }
                    }
                
                }
                result.setTotalCount(totalLength);
                result.setReturnedCount(contentData.size());
                result.setResults(contentData);
            } 

        } catch (RegistryException | IndexerException | DocumentationPersistenceException e) {
            throw new APIPersistenceException("Error while searching for content ", e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return result;
    }
    
    @Override
    public void changeAPILifeCycle(Organization org, String apiId, String status) throws APIPersistenceException {
        GenericArtifactManager artifactManager = null;
        boolean isTenantFlowStarted = false;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            Registry registry = holder.getRegistry();
            isTenantFlowStarted   = holder.isTenantFlowStarted();
            //PrivilegedCarbonContext.startTenantFlow();
            //PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(this.username);
            //PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(org.getName(), true);
            //GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);  /////////////////////////////???????
            if (GovernanceUtils.findGovernanceArtifactConfiguration(APIConstants.API_KEY, registry) != null) {
                artifactManager = new GenericArtifactManager(registry, APIConstants.API_KEY);
                GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiId);
                String action = LCManagerFactory.getInstance().getLCManager()
                        .getTransitionAction(apiArtifact.getLifecycleState().toUpperCase(), status.toUpperCase());
                apiArtifact.invokeAction(action, APIConstants.API_LIFE_CYCLE);
            } else {
                log.warn("Couldn't find GovernanceArtifactConfiguration of RXT: " + APIConstants.API_KEY +
                        ". Tenant id set in registry : " + ((UserRegistry) registry).getTenantId() +
                        ", Tenant domain set in PrivilegedCarbonContext: " +
                        PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId());
            }

        } catch (GovernanceException e) {
            throw new APIPersistenceException("Error while changing the lifecycle. ", e);
        } catch (RegistryException e) {
            throw new APIPersistenceException("Error while accessing the registry. ", e);
        } catch (PersistenceException e) {
            throw new APIPersistenceException("Error while accessing the lifecycle. ", e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        
    }

    @Override
    public void saveWSDL(Organization org, String apiId, ResourceFile wsdlResourceFile)
            throws WSDLPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            
            String apiSourcePath = RegistryPersistenceUtil.getAPIBasePath(apiProviderName, apiName, apiVersion);
            String wsdlResourcePath = null;
            boolean isZip = false;
            String wsdlResourcePathArchive = apiSourcePath + RegistryConstants.PATH_SEPARATOR
                    + APIConstants.API_WSDL_ARCHIVE_LOCATION + apiProviderName + APIConstants.WSDL_PROVIDER_SEPERATOR
                    + apiName + apiVersion + APIConstants.ZIP_FILE_EXTENSION;
            String wsdlResourcePathFile = apiSourcePath + RegistryConstants.PATH_SEPARATOR
                    + RegistryPersistenceUtil.createWsdlFileName(apiProviderName, apiName, apiVersion);
            if (APIConstants.APPLICATION_ZIP.equals(wsdlResourceFile.getContentType())) {
                wsdlResourcePath = wsdlResourcePathArchive;
                isZip = true;
            } else {
                wsdlResourcePath = wsdlResourcePathFile;
            }

            String visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);
            String visibleRolesList = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES);

            Resource wsdlResource = registry.newResource();;
            
            wsdlResource.setContentStream(wsdlResourceFile.getContent());
            if (wsdlResourceFile.getContentType() != null) {
                wsdlResource.setMediaType(wsdlResourceFile.getContentType());
            }
            registry.put(wsdlResourcePath, wsdlResource);
            //set the anonymous role for wsld resource to avoid basicauth security.
            String[] visibleRoles = null;
            if (visibleRolesList != null) {
                visibleRoles = visibleRolesList.split(",");
            }
            RegistryPersistenceUtil.setResourcePermissions(apiProviderName, visibility, visibleRoles, wsdlResourcePath);
            
            if (isZip) {
                //Delete any WSDL file if exists
                if (registry.resourceExists(wsdlResourcePathFile)) {
                    registry.delete(wsdlResourcePathFile);
                }
            } else {
                //Delete any WSDL archives if exists
                if (registry.resourceExists(wsdlResourcePathArchive)) {
                    registry.delete(wsdlResourcePathArchive);
                }
            }
            String absoluteWSDLResourcePath = RegistryUtils
                    .getAbsolutePath(RegistryContext.getBaseInstance(), RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH)
                    + wsdlResourcePath;
            String wsdlRegistryPath;
            if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME
                    .equalsIgnoreCase(tenantDomain)) {
                wsdlRegistryPath =
                        RegistryConstants.PATH_SEPARATOR + "registry" + RegistryConstants.PATH_SEPARATOR + "resource"
                                + absoluteWSDLResourcePath;
            } else {
                wsdlRegistryPath = "/t/" + tenantDomain + RegistryConstants.PATH_SEPARATOR + "registry"
                        + RegistryConstants.PATH_SEPARATOR + "resource" + absoluteWSDLResourcePath;
            }
            apiArtifact.setAttribute(APIConstants.API_OVERVIEW_WSDL, wsdlRegistryPath);
            apiArtifactManager.updateGenericArtifact(apiArtifact);
        } catch (APIPersistenceException | APIManagementException | RegistryException e) {
            throw new WSDLPersistenceException("Error while saving the wsdl for api " + apiId, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }

    }

    @Override
    public ResourceFile getWSDL(Organization org, String apiId) throws WSDLPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            if (apiArtifact == null) {
                return null;
            }
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);

            String apiSourcePath = RegistryPersistenceUtil.getAPIBasePath(apiProviderName, apiName, apiVersion);
            String wsdlResourcePath = apiSourcePath + RegistryConstants.PATH_SEPARATOR
                    + RegistryPersistenceUtil.createWsdlFileName(apiProviderName, apiName, apiVersion);
            String wsdlResourcePathOld = APIConstants.API_WSDL_RESOURCE_LOCATION
                    + RegistryPersistenceUtil.createWsdlFileName(apiProviderName, apiName, apiVersion);
            String resourceFileName = apiProviderName + "-" + apiName + "-" + apiVersion;
            if (registry.resourceExists(wsdlResourcePath)) {
                Resource resource = registry.get(wsdlResourcePath);
                ResourceFile returnResource = new ResourceFile(resource.getContentStream(), resource.getMediaType());
                returnResource.setName(resourceFileName);
                return returnResource;
            } else if (registry.resourceExists(wsdlResourcePathOld)) {
                Resource resource = registry.get(wsdlResourcePathOld);
                ResourceFile returnResource = new ResourceFile(resource.getContentStream(), resource.getMediaType());
                returnResource.setName(resourceFileName);
                return returnResource;
            } else {
                wsdlResourcePath = apiSourcePath + RegistryConstants.PATH_SEPARATOR
                        + APIConstants.API_WSDL_ARCHIVE_LOCATION + apiProviderName
                        + APIConstants.WSDL_PROVIDER_SEPERATOR + apiName + apiVersion + APIConstants.ZIP_FILE_EXTENSION;
                wsdlResourcePathOld = APIConstants.API_WSDL_RESOURCE_LOCATION + APIConstants.API_WSDL_ARCHIVE_LOCATION
                        + apiProviderName + APIConstants.WSDL_PROVIDER_SEPERATOR + apiName + apiVersion
                        + APIConstants.ZIP_FILE_EXTENSION;
                if (registry.resourceExists(wsdlResourcePath)) {
                    Resource resource = registry.get(wsdlResourcePath);
                    ResourceFile returnResource = new ResourceFile(resource.getContentStream(), resource.getMediaType());
                    returnResource.setName(resourceFileName);
                    return returnResource;
                } else if (registry.resourceExists(wsdlResourcePathOld)) {
                    Resource resource = registry.get(wsdlResourcePathOld);
                    ResourceFile returnResource = new ResourceFile(resource.getContentStream(), resource.getMediaType());
                    returnResource.setName(resourceFileName);
                    return returnResource;
                } else {
                    throw new WSDLPersistenceException("No WSDL found for the API: " + apiId,
                            ExceptionCodes.from(ExceptionCodes.NO_WSDL_AVAILABLE_FOR_API, apiName, apiVersion));
                }
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error while getting wsdl file from the registry for API: " + apiId.toString();
            throw new WSDLPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public void saveOASDefinition(Organization org, String apiId, String apiDefinition) throws OASPersistenceException {

        boolean isTenantFlowStarted = false;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            Registry registry = holder.getRegistry();
            isTenantFlowStarted  = holder.isTenantFlowStarted();
            //GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);/////////////////????/////////////
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);
            if (artifactManager == null) {
                String errorMessage = "Failed to retrieve artifact manager when deleting API " + apiId;
                log.error(errorMessage);
                throw new OASPersistenceException(errorMessage);
            }

            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiId);

            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            String visibleRoles = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES);
            String visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);
            String resourcePath = RegistryPersistenceUtil.getOpenAPIDefinitionFilePath(apiName, apiVersion,
                    apiProviderName);
            resourcePath = resourcePath + APIConstants.API_OAS_DEFINITION_RESOURCE_NAME;
            Resource resource;
            if (!registry.resourceExists(resourcePath)) {
                resource = registry.newResource();
            } else {
                resource = registry.get(resourcePath);
            }
            resource.setContent(apiDefinition);
            resource.setMediaType("application/json");
            registry.put(resourcePath, resource);

            String[] visibleRolesArr = null;
            if (visibleRoles != null) {
                visibleRolesArr = visibleRoles.split(",");
            }

            // Need to set anonymous if the visibility is public
            RegistryPersistenceUtil.clearResourcePermissions(resourcePath,
                    new APIIdentifier(apiProviderName, apiName, apiVersion), ((UserRegistry) registry).getTenantId());
            RegistryPersistenceUtil.setResourcePermissions(apiProviderName, visibility, visibleRolesArr, resourcePath);

        } catch (RegistryException | APIPersistenceException| APIManagementException e) {
            throw new OASPersistenceException("Error while adding OSA Definition for " + apiId, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public String getOASDefinition(Organization org, String apiId) throws OASPersistenceException {
        String apiTenantDomain = org.getName();
        String definition = null;
        boolean tenantFlowStarted = false;
        try {
            RegistryHolder holder = getRegistry(apiTenantDomain);
            Registry registryType = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted;
            /*
            //Tenant store anonymous mode if current tenant and the required tenant is not matching
            if (this.tenantDomain == null || isTenantDomainNotMatching(apiTenantDomain)) {
                if (apiTenantDomain != null) {
                    RegistryPersistenceUtil.startTenantFlow(apiTenantDomain);
                    tenantFlowStarted = true;
                }
                int tenantId = getTenantManager().getTenantId(
                        apiTenantDomain);
                registryType = getRegistryService().getGovernanceUserRegistry(
                        CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                registryType = registry;
            } */
            Identifier id = null;
            GenericArtifactManager artifactManager = RegistryPersistenceUtil.getArtifactManager(registryType,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiId);
            if (apiArtifact != null) {

                String type = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_TYPE);
                if ("APIProduct".equals(type)) {
                    id = new APIProductIdentifier(apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER),
                            apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME),
                            apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                } else {
                    id = new APIIdentifier(apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER),
                            apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME),
                            apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION));
                }
                definition = RegistryPersistenceUtil.getAPIDefinition(id, registryType);
            }
            /*if (log.isDebugEnabled()) {
                log.debug("Definition for " + apiId + " : " +  definition);
            }*/
        } catch (RegistryException | APIManagementException | APIPersistenceException e) {
            String msg = "Failed to get swagger documentation of API : " + apiId;
            throw new OASPersistenceException(msg, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
        return definition;
    }
/*
    private boolean isTenantDomainNotMatching(String tenantDomain) {
        if (this.tenantDomain != null) {
            return !(this.tenantDomain.equals(tenantDomain));
        }
        return true;
    } */
    @Override
    public void saveGraphQLSchemaDefinition(Organization org, String apiId, String schemaDefinition)
            throws GraphQLPersistenceException {
        boolean tenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new GraphQLPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String path = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR + api.apiVersion
                    + RegistryConstants.PATH_SEPARATOR;

            String saveResourcePath = path + api.apiProvider + APIConstants.GRAPHQL_SCHEMA_PROVIDER_SEPERATOR
                    + api.apiName + api.apiVersion + APIConstants.GRAPHQL_SCHEMA_FILE_EXTENSION;
            Resource resource;
            if (!registry.resourceExists(saveResourcePath)) {
                resource = registry.newResource();
            } else {
                resource = registry.get(saveResourcePath);
            }

            resource.setContent(schemaDefinition);
            resource.setMediaType(String.valueOf(ContentType.TEXT_PLAIN));
            registry.put(saveResourcePath, resource);
            if (log.isDebugEnabled()) {
                log.debug("Successfully imported the schema: " + schemaDefinition);
            }

            // Need to set anonymous if the visibility is public
            RegistryPersistenceUtil.clearResourcePermissions(saveResourcePath,
                    new APIIdentifier(api.apiProvider, api.apiName, api.apiVersion),
                    ((UserRegistry) registry).getTenantId());
            RegistryPersistenceUtil.setResourcePermissions(api.apiProvider, api.visibility, api.visibleRoles,
                    saveResourcePath);

        } catch (RegistryException | APIManagementException | APIPersistenceException e) {
            throw new GraphQLPersistenceException("Error while adding Graphql Definition for api " + apiId, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
    }

    @Override
    public String getGraphQLSchema(Organization org, String apiId) throws GraphQLPersistenceException {
        boolean tenantFlowStarted = false;
        String schemaDoc = null;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new GraphQLPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String path = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR + api.apiVersion
                    + RegistryConstants.PATH_SEPARATOR;
            String schemaName = api.apiProvider + APIConstants.GRAPHQL_SCHEMA_PROVIDER_SEPERATOR + api.apiName
                    + api.apiVersion + APIConstants.GRAPHQL_SCHEMA_FILE_EXTENSION;
            String schemaResourePath = path + schemaName;
            if (registry.resourceExists(schemaResourePath)) {
                Resource schemaResource = registry.get(schemaResourePath);
                schemaDoc = IOUtils.toString(schemaResource.getContentStream(),
                        RegistryConstants.DEFAULT_CHARSET_ENCODING);
            }
        } catch (APIPersistenceException | RegistryException | IOException e) {
            throw new GraphQLPersistenceException("Error while accessing graphql schema definition ", e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
        return schemaDoc;
    }

    @Override
    public Documentation addDocumentation(Organization org, String apiId, Documentation documentation)
            throws DocumentationPersistenceException {
        boolean tenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted();
            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            
            GenericArtifactManager docArtifactManager = new GenericArtifactManager(registry,
                    APIConstants.DOCUMENTATION_KEY);
            GenericArtifact docArtifact = docArtifactManager.newGovernanceArtifact(new QName(documentation.getName()));
            docArtifactManager.addGenericArtifact(RegistryPersistanceDocUtil.createDocArtifactContent(docArtifact,
                    apiName, apiVersion, apiProviderName, documentation));           
            
            String apiPath = RegistryPersistenceUtil.getAPIPath(apiName, apiVersion, apiProviderName);
            String docVisibility = documentation.getVisibility().name();
            String[] authorizedRoles = RegistryPersistenceUtil.getAuthorizedRoles(apiPath, tenantDomain);
            String visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);
            if (docVisibility != null) {
                if (APIConstants.DOC_SHARED_VISIBILITY.equalsIgnoreCase(docVisibility)) {
                    authorizedRoles = null;
                    visibility = APIConstants.DOC_SHARED_VISIBILITY;
                } else if (APIConstants.DOC_OWNER_VISIBILITY.equalsIgnoreCase(docVisibility)) {
                    authorizedRoles = null;
                    visibility = APIConstants.DOC_OWNER_VISIBILITY;
                }
            }
            RegistryPersistenceUtil.setResourcePermissions(apiProviderName,visibility, authorizedRoles, docArtifact
                    .getPath(), registry);
            String docFilePath = docArtifact.getAttribute(APIConstants.DOC_FILE_PATH);
            if (docFilePath != null && !"".equals(docFilePath)) {
                // The docFilePatch comes as
                // /t/tenanatdoman/registry/resource/_system/governance/apimgt/applicationdata..
                // We need to remove the /t/tenanatdoman/registry/resource/_system/governance section to set
                // permissions.
                int startIndex = docFilePath.indexOf(APIConstants.GOVERNANCE) + (APIConstants.GOVERNANCE).length();
                String filePath = docFilePath.substring(startIndex, docFilePath.length());
                RegistryPersistenceUtil.setResourcePermissions(apiProviderName, visibility, authorizedRoles, filePath,
                        registry);
            }
            documentation.setId(docArtifact.getId());
            return documentation;
        } catch (RegistryException | APIManagementException | UserStoreException | APIPersistenceException e) {
            throw new DocumentationPersistenceException("Failed to add documentation", e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
    }

    @Override
    public Documentation updateDocumentation(Organization org, String apiId, Documentation documentation)
            throws DocumentationPersistenceException {
        boolean tenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            tenantFlowStarted = holder.isTenantFlowStarted();
            
            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);

            GenericArtifactManager artifactManager = RegistryPersistanceDocUtil.getDocumentArtifactManager(registry);
            GenericArtifact artifact = artifactManager.getGenericArtifact(documentation.getId());
            String docVisibility = documentation.getVisibility().name();
            String[] authorizedRoles = new String[0];
            String visibleRolesList = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES);
            if (visibleRolesList != null) {
                authorizedRoles = visibleRolesList.split(",");
            }
            String visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);
            if (docVisibility != null) {
                if (APIConstants.DOC_SHARED_VISIBILITY.equalsIgnoreCase(docVisibility)) {
                    authorizedRoles = null;
                    visibility = APIConstants.DOC_SHARED_VISIBILITY;
                } else if (APIConstants.DOC_OWNER_VISIBILITY.equalsIgnoreCase(docVisibility)) {
                    authorizedRoles = null;
                    visibility = APIConstants.DOC_OWNER_VISIBILITY;
                }
            }

            GenericArtifact updateApiArtifact = RegistryPersistanceDocUtil.createDocArtifactContent(artifact,
                    apiProviderName, apiName, apiVersion, documentation);
            artifactManager.updateGenericArtifact(updateApiArtifact);
            RegistryPersistenceUtil.clearResourcePermissions(updateApiArtifact.getPath(),
                    new APIIdentifier(apiProviderName, apiName, apiVersion), ((UserRegistry) registry).getTenantId());

            RegistryPersistenceUtil.setResourcePermissions(apiProviderName, visibility, authorizedRoles,
                    artifact.getPath(), registry);

            String docFilePath = artifact.getAttribute(APIConstants.DOC_FILE_PATH);
            if (docFilePath != null && !"".equals(docFilePath)) {
                // The docFilePatch comes as
                // /t/tenanatdoman/registry/resource/_system/governance/apimgt/applicationdata..
                // We need to remove the
                // /t/tenanatdoman/registry/resource/_system/governance section
                // to set permissions.
                int startIndex = docFilePath.indexOf(APIConstants.GOVERNANCE) + (APIConstants.GOVERNANCE).length();
                String filePath = docFilePath.substring(startIndex, docFilePath.length());
                RegistryPersistenceUtil.setResourcePermissions(apiProviderName, visibility, authorizedRoles, filePath,
                        registry);
            }
            return documentation;
        } catch (RegistryException | APIManagementException | APIPersistenceException e) {
            throw new DocumentationPersistenceException("Failed to update documentation", e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
    }

    @Override
    public Documentation getDocumentation(Organization org, String apiId, String docId)
            throws DocumentationPersistenceException {
        Documentation documentation = null;
        boolean tenantFlowStarted = false;
        try {
            String requestedTenantDomain = org.getName();
            RegistryHolder holder = getRegistry(requestedTenantDomain);
            Registry registryType = holder.getRegistry();
            tenantFlowStarted  = holder.isTenantFlowStarted();
            /*
            boolean isTenantMode = (requestedTenantDomain != null);
            // Tenant store anonymous mode if current tenant and the required tenant is not matching
            if ((isTenantMode && this.tenantDomain == null)
                    || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {
                int tenantId = getTenantManager().getTenantId(requestedTenantDomain);
                registryType = getRegistryService()
                        .getGovernanceUserRegistry(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                registryType = registry;
            }*/
            GenericArtifactManager artifactManager = RegistryPersistanceDocUtil
                    .getDocumentArtifactManager(registryType);
            GenericArtifact artifact = artifactManager.getGenericArtifact(docId);
            
            if (artifact == null) {
                return documentation;
            }
            if (null != artifact) {
                documentation = RegistryPersistanceDocUtil.getDocumentation(artifact);
                documentation.setCreatedDate(registryType.get(artifact.getPath()).getCreatedTime());
                Date lastModified = registryType.get(artifact.getPath()).getLastModified();
                if (lastModified != null) {
                    documentation.setLastUpdated(registryType.get(artifact.getPath()).getLastModified());
                }
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Failed to get documentation details";
            throw new DocumentationPersistenceException(msg, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
        return documentation;
    }

    @Override
    public DocumentContent getDocumentationContent(Organization org, String apiId, String docId)
            throws DocumentationPersistenceException {
        DocumentContent documentContent = null;
        boolean tenantFlowStarted = false;
        try {
            String requestedTenantDomain = org.getName();
            RegistryHolder holder = getRegistry(requestedTenantDomain);
            Registry registryType = holder.getRegistry();
            tenantFlowStarted  = holder.isTenantFlowStarted();
            /*
            boolean isTenantMode = (requestedTenantDomain != null);
            // Tenant store anonymous mode if current tenant and the required tenant is not matching
            if ((isTenantMode && this.tenantDomain == null)
                    || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {
                int tenantId = getTenantManager().getTenantId(requestedTenantDomain);
                registryType = getRegistryService()
                        .getGovernanceUserRegistry(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                registryType = registry;
            }*/
            GenericArtifactManager artifactManager = RegistryPersistanceDocUtil
                    .getDocumentArtifactManager(registryType);
            GenericArtifact artifact = artifactManager.getGenericArtifact(docId);
            
            if (artifact == null) {
                return null;
            }
            if (artifact != null) {
                Documentation documentation = RegistryPersistanceDocUtil.getDocumentation(artifact);
                if (documentation.getSourceType().equals(Documentation.DocumentSourceType.FILE)) {
                    String resource = documentation.getFilePath();
                    String[] resourceSplitPath =
                            resource.split(RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH);
                    if (resourceSplitPath.length == 2) {
                        resource = resourceSplitPath[1];
                    } else {
                        throw new DocumentationPersistenceException("Invalid resource Path " + resource);
                    }
                    if (registryType.resourceExists(resource)) {
                        documentContent = new DocumentContent();
                        Resource apiDocResource = registryType.get(resource);
                        String[] content = apiDocResource.getPath().split("/");
                        String name = content[content.length - 1];

                        documentContent.setSourceType(ContentSourceType.FILE);
                        ResourceFile resourceFile = new ResourceFile(
                                apiDocResource.getContentStream(), apiDocResource.getMediaType());
                        resourceFile.setName(name);
                        documentContent.setResourceFile(resourceFile);
                    }

                } else if (documentation.getSourceType().equals(Documentation.DocumentSourceType.INLINE)
                        || documentation.getSourceType().equals(Documentation.DocumentSourceType.MARKDOWN)) {
                    
                    String contentPath = artifact.getPath()
                            .replace(RegistryConstants.PATH_SEPARATOR + documentation.getName(), "")
                            + RegistryConstants.PATH_SEPARATOR + APIConstants.INLINE_DOCUMENT_CONTENT_DIR
                            + RegistryConstants.PATH_SEPARATOR + documentation.getName();
                    if (registryType.resourceExists(contentPath)) {
                        documentContent = new DocumentContent();
                        Resource docContent = registryType.get(contentPath);
                        Object content = docContent.getContent();
                        if (content != null) {
                            String contentStr = new String((byte[]) docContent.getContent(), Charset.defaultCharset());
                            documentContent.setTextContent(contentStr);
                            documentContent
                                    .setSourceType(ContentSourceType.valueOf(documentation.getSourceType().toString()));
                        }
                    }
                } else if (documentation.getSourceType().equals(Documentation.DocumentSourceType.URL)) {
                    documentContent = new DocumentContent();
                    String sourceUrl = documentation.getSourceUrl();
                    documentContent.setTextContent(sourceUrl);
                    documentContent
                            .setSourceType(ContentSourceType.valueOf(documentation.getSourceType().toString()));
                }
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Failed to get documentation details";
            throw new DocumentationPersistenceException(msg, e);
        } finally {
            if (tenantFlowStarted) {
                RegistryPersistenceUtil.endTenantFlow();
            }
        }
        return documentContent;
    }
    
    @Override
    public DocumentContent addDocumentationContent(Organization org, String apiId, String docId,
            DocumentContent content) throws DocumentationPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            
            GenericArtifactManager docArtifactManager = RegistryPersistanceDocUtil
                    .getDocumentArtifactManager(registry);
            GenericArtifact docArtifact = docArtifactManager.getGenericArtifact(docId);
            Documentation doc = RegistryPersistanceDocUtil.getDocumentation(docArtifact);

            if (DocumentContent.ContentSourceType.FILE.equals(content.getSourceType())) {
                ResourceFile resource = content.getResourceFile();
                String filePath = RegistryPersistanceDocUtil.getDocumentFilePath(apiProviderName, apiName, apiVersion,
                        resource.getName());
                String visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);
                String visibleRolesList = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES);
                String[] visibleRoles = new String[0];
                if (visibleRolesList != null) {
                    visibleRoles = visibleRolesList.split(",");
                }
                RegistryPersistenceUtil.setResourcePermissions(
                        RegistryPersistenceUtil.replaceEmailDomain(apiProviderName), visibility, visibleRoles, filePath,
                        registry);
                //documentation.setFilePath(addResourceFile(apiId, filePath, icon));
                String savedFilePath = addResourceFile(filePath, resource, registry, tenantDomain);
                //doc.setFilePath(savedFilePath);
                docArtifact.setAttribute(APIConstants.DOC_FILE_PATH, savedFilePath);
                docArtifactManager.updateGenericArtifact(docArtifact);
                RegistryPersistenceUtil.setFilePermission(filePath);
            } else {
                String contentPath = RegistryPersistanceDocUtil.getDocumentContentPath(apiProviderName, apiName,
                        apiVersion, doc.getName());
                Resource docContent;

                if (!registry.resourceExists(contentPath)) {
                    docContent = registry.newResource();
                } else {
                    docContent = registry.get(contentPath);
                }
                String text = content.getTextContent();
                if (!APIConstants.NO_CONTENT_UPDATE.equals(text)) {
                    docContent.setContent(text);
                }
                docContent.setMediaType(APIConstants.DOCUMENTATION_INLINE_CONTENT_TYPE);
                registry.put(contentPath, docContent);            
                
                // Set resource permission
                String apiPath = RegistryPersistenceUtil.getAPIPath(apiName, apiVersion, apiProviderName);
                String docVisibility = doc.getVisibility().name();
                String[] authorizedRoles = RegistryPersistenceUtil.getAuthorizedRoles(apiPath, tenantDomain);
                String visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);
                if (docVisibility != null) {
                    if (APIConstants.DOC_SHARED_VISIBILITY.equalsIgnoreCase(docVisibility)) {
                        authorizedRoles = null;
                        visibility = APIConstants.DOC_SHARED_VISIBILITY;
                    } else if (APIConstants.DOC_OWNER_VISIBILITY.equalsIgnoreCase(docVisibility)) {
                        authorizedRoles = null;
                        visibility = APIConstants.DOC_OWNER_VISIBILITY;
                    }
                }
                RegistryPersistenceUtil.setResourcePermissions(apiProviderName, visibility, authorizedRoles,
                        contentPath, registry);
            } 
        } catch (APIPersistenceException | RegistryException | APIManagementException | PersistenceException
                | UserStoreException e) {
            throw new DocumentationPersistenceException("Error while adding document content", e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return null;
    }

    @Override
    public DocumentSearchResult searchDocumentation(Organization org, String apiId, int start, int offset,
            String searchQuery, UserContext ctx) throws DocumentationPersistenceException {

        DocumentSearchResult result = null;
        Registry registryType;
        String requestedTenantDomain = org.getName();
        boolean isTenantFlowStarted = false;
        try {
            
            RegistryHolder holder = getRegistry(requestedTenantDomain);
            registryType = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            /*
            boolean isTenantMode = (requestedTenantDomain != null);
            if (isTenantMode && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(requestedTenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(requestedTenantDomain, true);
            } else {
                requestedTenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(requestedTenantDomain, true);
            }

            // Tenant store anonymous mode if current tenant and the required tenant is not matching
            if ((isTenantMode && this.tenantDomain == null)
                    || (isTenantMode && isTenantDomainNotMatching(requestedTenantDomain))) {
                int tenantId = getTenantManager().getTenantId(requestedTenantDomain);
                registryType = getRegistryService()
                        .getGovernanceUserRegistry(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, tenantId);
            } else {
                registryType = registry;
            } */

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registryType,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);

            String apiOrAPIProductDocPath = RegistryPersistanceDocUtil.getDocumentPath(apiProviderName, apiName,
                    apiVersion);
            String pathToContent = apiOrAPIProductDocPath + APIConstants.INLINE_DOCUMENT_CONTENT_DIR;
            String pathToDocFile = apiOrAPIProductDocPath + APIConstants.DOCUMENT_FILE_DIR;

            if (registryType.resourceExists(apiOrAPIProductDocPath)) {
                List<Documentation> documentationList = new ArrayList<Documentation>();
                Resource resource = registryType.get(apiOrAPIProductDocPath);
                if (resource instanceof org.wso2.carbon.registry.core.Collection) {
                    String[] docsPaths = ((org.wso2.carbon.registry.core.Collection) resource).getChildren();
                    for (String docPath : docsPaths) {
                        if (!(docPath.equalsIgnoreCase(pathToContent) || docPath.equalsIgnoreCase(pathToDocFile))) {
                            Resource docResource = registryType.get(docPath);
                            GenericArtifactManager artifactManager = RegistryPersistanceDocUtil
                                    .getDocumentArtifactManager(registryType);
                            GenericArtifact docArtifact = artifactManager.getGenericArtifact(docResource.getUUID());
                            Documentation doc = RegistryPersistanceDocUtil.getDocumentation(docArtifact);
                            if (searchQuery != null) {
                                if (searchQuery.toLowerCase().startsWith("name:")) {
                                    String requestedDocName = searchQuery.split(":")[1];
                                    if (doc.getName().equalsIgnoreCase(requestedDocName)) {
                                        documentationList.add(doc);
                                    }
                                } else {
                                    log.warn("Document search not implemented for the query " + searchQuery);
                                }
                            } else {
                                documentationList.add(doc);
                            }
                            
                        }
                    }
                }
                result = new DocumentSearchResult();
                result.setDocumentationList(documentationList);
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Failed to get documentations for api/product " + apiId;
            throw new DocumentationPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return result;
    }

    @Override
    public void deleteDocumentation(Organization org, String apiId, String docId)
            throws DocumentationPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            RegistryHolder holder = getRegistry(org.getName());
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            GenericArtifactManager artifactManager = RegistryPersistanceDocUtil.getDocumentArtifactManager(registry);
            if (artifactManager == null) {
                String errorMessage = "Failed to retrieve artifact manager when removing documentation of " + apiId
                        + " Document ID " + docId;
                log.error(errorMessage);
                throw new DocumentationPersistenceException(errorMessage);
            }
            GenericArtifact artifact = artifactManager.getGenericArtifact(docId);
            String docPath = artifact.getPath();
            if (docPath != null) {
                if (registry.resourceExists(docPath)) {
                    registry.delete(docPath);
                }
            }

        } catch (RegistryException | APIPersistenceException e) {
            throw new DocumentationPersistenceException("Failed to delete documentation", e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public Mediation addMediationPolicy(Organization org, String apiId, Mediation mediation)
            throws MediationPolicyPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new MediationPolicyPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String resourcePath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR + api.apiVersion
                    + RegistryConstants.PATH_SEPARATOR + mediation.getType() + RegistryConstants.PATH_SEPARATOR
                    + mediation.getName();

            if (registry.resourceExists(resourcePath)) {
                throw new MediationPolicyPersistenceException(
                        "Mediation policy already exists for the given name " + mediation.getName(),
                        ExceptionCodes.MEDIATION_POLICY_API_ALREADY_EXISTS);
            }
            Resource policy = registry.newResource();
            policy.setContent(mediation.getConfig());
            policy.setMediaType("application/xml");
            registry.put(resourcePath, policy);
            
            mediation.setId(policy.getUUID());
            return mediation;
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error while adding the mediation to the registry";
            throw new MediationPolicyPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public Mediation updateMediationPolicy(Organization org, String apiId, Mediation mediation)
            throws MediationPolicyPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new MediationPolicyPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String resourcePath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR + api.apiVersion
                    + RegistryConstants.PATH_SEPARATOR + mediation.getType() + RegistryConstants.PATH_SEPARATOR
                    + mediation.getName();

            Resource policy = registry.get(resourcePath);
            policy.setContent(mediation.getConfig());
            registry.put(resourcePath, policy);
            return mediation;
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error while adding the mediation to the registry";
            throw new MediationPolicyPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public Mediation getMediationPolicy(Organization org, String apiId, String mediationPolicyId)
            throws MediationPolicyPersistenceException {
        boolean isTenantFlowStarted = false;
        Mediation mediation = null;

        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new MediationPolicyPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String apiResourcePath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR
                    + api.apiVersion;

            // apiResourcePath = apiResourcePath.substring(0, apiResourcePath.lastIndexOf("/"));
            // Getting API registry resource
            Resource resource = registry.get(apiResourcePath);
            // resource eg: /_system/governance/apimgt/applicationdata/provider/admin/calculatorAPI/2.0
            if (resource instanceof Collection) {
                Collection typeCollection = (Collection) resource;
                String[] typeArray = typeCollection.getChildren();
                for (String type : typeArray) {
                    // Check for mediation policy resource
                    if ((type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                            + APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN))
                            || (type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT))
                            || (type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT))) {
                        Resource sequenceType = registry.get(type);
                        // sequenceType eg: in / out /fault
                        if (sequenceType instanceof Collection) {
                            String[] mediationPolicyArr = ((Collection) sequenceType).getChildren();
                            for (String mediationPolicy : mediationPolicyArr) {
                                Resource mediationResource = registry.get(mediationPolicy);
                                String resourceId = mediationResource.getUUID();
                                if (resourceId.equalsIgnoreCase(mediationPolicyId)) {
                                    // Get mediation policy config content
                                    String contentString = IOUtils.toString(mediationResource.getContentStream(),
                                            RegistryConstants.DEFAULT_CHARSET_ENCODING);
                                    // Extracting name specified in the mediation config
                                    OMElement omElement = AXIOMUtil.stringToOM(contentString);
                                    OMAttribute attribute = omElement.getAttribute(new QName("name"));
                                    String mediationPolicyName = attribute.getAttributeValue();

                                    String resourcePath = mediationResource.getPath();
                                    String[] path = resourcePath.split(RegistryConstants.PATH_SEPARATOR);
                                    String resourceType = path[(path.length - 2)];
                                    mediation = new Mediation();
                                    mediation.setConfig(contentString);
                                    mediation.setType(resourceType);
                                    mediation.setId(mediationResource.getUUID());
                                    mediation.setName(mediationPolicyName);
                                }
                            }
                        }
                    }
                }
            }
        } catch (RegistryException | APIPersistenceException | IOException | XMLStreamException e) {
            String msg = "Error occurred  while getting Api Specific mediation policies ";
            throw new MediationPolicyPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return mediation;
    }

    @Override
    public List<MediationInfo> getAllMediationPolicies(Organization org, String apiId)
            throws MediationPolicyPersistenceException {
        boolean isTenantFlowStarted = false;
        List<MediationInfo> mediationList = new ArrayList<MediationInfo>();
        MediationInfo mediation;

        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new MediationPolicyPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String apiResourcePath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR
                    + api.apiVersion;

            // apiResourcePath = apiResourcePath.substring(0, apiResourcePath.lastIndexOf("/"));
            // Getting API registry resource
            Resource resource = registry.get(apiResourcePath);
            // resource eg: /_system/governance/apimgt/applicationdata/provider/admin/calculatorAPI/2.0
            if (resource instanceof Collection) {
                Collection typeCollection = (Collection) resource;
                String[] typeArray = typeCollection.getChildren();
                for (String type : typeArray) {
                    // Check for mediation policy sequences
                    if ((type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                            + APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN))
                            || (type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT))
                            || (type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT))) {
                        Resource typeResource = registry.get(type);
                        // typeResource : in / out / fault
                        if (typeResource instanceof Collection) {
                            String[] mediationPolicyArr = ((Collection) typeResource).getChildren();
                            if (mediationPolicyArr.length > 0) {
                                for (String mediationPolicy : mediationPolicyArr) {
                                    Resource policyResource = registry.get(mediationPolicy);
                                    // policyResource eg: custom_in_message

                                    // Get uuid of the registry resource
                                    String resourceId = policyResource.getUUID();

                                    // Get mediation policy config
                                    try {
                                        String contentString = IOUtils.toString(policyResource.getContentStream(),
                                                RegistryConstants.DEFAULT_CHARSET_ENCODING);
                                        // Extract name from the policy config
                                        OMElement omElement = AXIOMUtil.stringToOM(contentString);
                                        OMAttribute attribute = omElement.getAttribute(new QName("name"));
                                        String mediationPolicyName = attribute.getAttributeValue();
                                        mediation = new MediationInfo();
                                        mediation.setId(resourceId);
                                        mediation.setName(mediationPolicyName);
                                        // Extracting mediation policy type from the registry resource path
                                        String resourceType = type.substring(type.lastIndexOf("/") + 1);
                                        mediation.setType(resourceType);
                                        mediationList.add(mediation);
                                    } catch (XMLStreamException e) {
                                        // If exception been caught flow will continue with next mediation policy
                                        log.error(
                                                "Error occurred while getting omElement out of" + " mediation content",
                                                e);
                                    } catch (IOException e) {
                                        log.error("Error occurred while converting the content "
                                                + "stream of mediation " + mediationPolicy + " to string", e);
                                    }

                                }
                            }
                        }
                    }
                }
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error occurred  while getting Api Specific mediation policies ";
            throw new MediationPolicyPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return mediationList;
    }

    @Override
    public void deleteMediationPolicy(Organization org, String apiId, String mediationPolicyId)
            throws MediationPolicyPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();
            BasicAPI api = getbasicAPIInfo(apiId, registry);
            if (api == null) {
                throw new MediationPolicyPersistenceException("API not foud ", ExceptionCodes.API_NOT_FOUND);
            }
            String apiResourcePath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + api.apiProvider
                    + RegistryConstants.PATH_SEPARATOR + api.apiName + RegistryConstants.PATH_SEPARATOR
                    + api.apiVersion;

            // apiResourcePath = apiResourcePath.substring(0, apiResourcePath.lastIndexOf("/"));
            // Getting API registry resource
            Resource resource = registry.get(apiResourcePath);
            // resource eg: /_system/governance/apimgt/applicationdata/provider/admin/calculatorAPI/2.0
            if (resource instanceof Collection) {
                Collection typeCollection = (Collection) resource;
                String[] typeArray = typeCollection.getChildren();
                for (String type : typeArray) {
                    // Check for mediation policy resource
                    if ((type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                            + APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN))
                            || (type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT))
                            || (type.equalsIgnoreCase(apiResourcePath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT))) {
                        Resource sequenceType = registry.get(type);
                        // sequenceType eg: in / out /fault
                        if (sequenceType instanceof Collection) {
                            String[] mediationPolicyArr = ((Collection) sequenceType).getChildren();
                            for (String mediationPolicy : mediationPolicyArr) {
                                Resource mediationResource = registry.get(mediationPolicy);
                                String resourceId = mediationResource.getUUID();
                                if (resourceId.equalsIgnoreCase(mediationPolicyId)) {
                                    registry.delete(mediationPolicy);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error occurred  while getting Api Specific mediation policies ";
            throw new MediationPolicyPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public void saveThumbnail(Organization org, String apiId, ResourceFile resourceFile)
            throws ThumbnailPersistenceException {
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            Registry registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            if (apiArtifact == null) {
                throw new ThumbnailPersistenceException("API not found. ", ExceptionCodes.API_NOT_FOUND);
            }
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);

            String artifactPath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR +
                    apiProviderName + RegistryConstants.PATH_SEPARATOR +
                    apiName + RegistryConstants.PATH_SEPARATOR + apiVersion;
            String filePath =  artifactPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;
            
            String savedFilePath = addResourceFile(filePath, resourceFile, registry, tenantDomain);

            RegistryPersistenceUtil.setResourcePermissions(apiProviderName, null, null, filePath);

            apiArtifact.setAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL, savedFilePath);
            apiArtifactManager.updateGenericArtifact(apiArtifact);
        } catch (APIPersistenceException | GovernanceException | PersistenceException | APIManagementException e) {
            throw new ThumbnailPersistenceException("Error while saving thumbnail for api " + apiId, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public ResourceFile getThumbnail(Organization org, String apiId) throws ThumbnailPersistenceException {

        Registry registry;
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            if (apiArtifact == null) {
                return null;
            }
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            
            String artifactOldPath = APIConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR
                    + apiProviderName + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR
                    + apiVersion;
            String artifactPath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + apiProviderName
                    + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR + apiVersion;
            
            String oldThumbPath = artifactOldPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;
            String thumbPath = artifactPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;

            if (registry.resourceExists(thumbPath)) {
                Resource res = registry.get(thumbPath);
                return new ResourceFile(res.getContentStream(), res.getMediaType());
            } else if (registry.resourceExists(oldThumbPath)){
                Resource res = registry.get(oldThumbPath);
                return new ResourceFile(res.getContentStream(), res.getMediaType());
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error while loading API icon of API " +  apiId + " from the registry";
            throw new ThumbnailPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return null;
    }

    @Override
    public void deleteThumbnail(Organization org, String apiId) throws ThumbnailPersistenceException {
        Registry registry;
        boolean isTenantFlowStarted = false;
        try {
            String tenantDomain = org.getName();
            RegistryHolder holder = getRegistry(tenantDomain);
            registry = holder.getRegistry();
            isTenantFlowStarted = holder.isTenantFlowStarted();

            GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                    APIConstants.API_KEY);

            GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(apiId);
            if (apiArtifact == null) {
                throw new ThumbnailPersistenceException("API not found for id " + apiId, ExceptionCodes.API_NOT_FOUND);
            }
            String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            apiProviderName = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
            String apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            
            String artifactOldPath = APIConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR
                    + apiProviderName + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR
                    + apiVersion;
            String artifactPath = APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + apiProviderName
                    + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR + apiVersion;
            
            String oldThumbPath = artifactOldPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;
            String thumbPath = artifactPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;

            if (registry.resourceExists(thumbPath)) {
                registry.delete(thumbPath);
            }
            if (registry.resourceExists(oldThumbPath)) {
                registry.delete(oldThumbPath);
            }
        } catch (RegistryException | APIPersistenceException e) {
            String msg = "Error while loading API icon of API " +  apiId + " from the registry";
            throw new ThumbnailPersistenceException(msg, e);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }
    
    /**
     * Persist API Status into a property of API Registry resource
     *
     * @param artifactId API artifact ID
     * @param apiStatus  Current status of the API
     * @throws APIManagementException on error
     */
    private void saveAPIStatus(Registry registry, String artifactId, String apiStatus) throws APIManagementException {
        try {
            Resource resource = registry.get(artifactId);
            if (resource != null) {
                String propValue = resource.getProperty(APIConstants.API_STATUS);
                if (propValue == null) {
                    resource.addProperty(APIConstants.API_STATUS, apiStatus);
                } else {
                    resource.setProperty(APIConstants.API_STATUS, apiStatus);
                }
                registry.put(artifactId, resource);
            }
        } catch (RegistryException e) {
            handleException("Error while adding API", e);
        }
    }
    /**
     * To add API/Product roles restrictions and add additional properties.
     *
     * @param artifactPath                Path of the API/Product artifact.
     * @param publisherAccessControlRoles Role specified for the publisher access control.
     * @param publisherAccessControl      Publisher Access Control restriction.
     * @param additionalProperties        Additional properties that is related with an API/Product.
     * @throws RegistryException Registry Exception.
     */
    private void updateRegistryResources(Registry registry, String artifactPath, String publisherAccessControlRoles,
                                    String publisherAccessControl, Map<String, String> additionalProperties)
                                    throws RegistryException {
        publisherAccessControlRoles = (publisherAccessControlRoles == null || publisherAccessControlRoles.trim()
                                        .isEmpty()) ? APIConstants.NULL_USER_ROLE_LIST : publisherAccessControlRoles;
        if (publisherAccessControlRoles.equalsIgnoreCase(APIConstants.NULL_USER_ROLE_LIST)) {
            publisherAccessControl = APIConstants.NO_ACCESS_CONTROL;
        }
        if (!registry.resourceExists(artifactPath)) {
            return;
        }

        Resource apiResource = registry.get(artifactPath);
        if (apiResource != null) {
            if (additionalProperties != null) {
                // Removing all the properties, before updating new properties.
                Properties properties = apiResource.getProperties();
                if (properties != null) {
                    Enumeration propertyNames = properties.propertyNames();
                    while (propertyNames.hasMoreElements()) {
                        String propertyName = (String) propertyNames.nextElement();
                        if (propertyName.startsWith(APIConstants.API_RELATED_CUSTOM_PROPERTIES_PREFIX)) {
                            apiResource.removeProperty(propertyName);
                        }
                    }
                }
            }
            // We are changing to lowercase, as registry search only supports lower-case characters.
            apiResource.setProperty(APIConstants.PUBLISHER_ROLES, publisherAccessControlRoles.toLowerCase());

            // This property will be only used for display proposes in the Publisher UI so that the original case of
            // the roles that were specified can be maintained.
            apiResource.setProperty(APIConstants.DISPLAY_PUBLISHER_ROLES, publisherAccessControlRoles);
            apiResource.setProperty(APIConstants.ACCESS_CONTROL, publisherAccessControl);
            apiResource.removeProperty(APIConstants.CUSTOM_API_INDEXER_PROPERTY);
            if (additionalProperties != null && additionalProperties.size() != 0) {
                for (Map.Entry<String, String> entry : additionalProperties.entrySet()) {
                    apiResource.setProperty((APIConstants.API_RELATED_CUSTOM_PROPERTIES_PREFIX + entry.getKey()),
                                                    entry.getValue());
                }
            }
            registry.put(artifactPath, apiResource);
        }
    }
    
    protected int getMaxPaginationLimit() {
        // TODO fix this
        /*
        String paginationLimit = getAPIManagerConfiguration()
                .getFirstProperty(APIConstants.API_STORE_APIS_PER_PAGE);
        // If the Config exists use it to set the pagination limit
        final int maxPaginationLimit;
        if (paginationLimit != null) {
            // The additional 1 added to the maxPaginationLimit is to help us determine if more
            // APIs may exist so that we know that we are unable to determine the actual total
            // API count. We will subtract this 1 later on so that it does not interfere with
            // the logic of the rest of the application
            int pagination = Integer.parseInt(paginationLimit);
            // Because the store jaggery pagination logic is 10 results per a page we need to set pagination
            // limit to at least 11 or the pagination done at this level will conflict with the store pagination
            // leading to some of the APIs not being displayed
            if (pagination < 11) {
                pagination = 11;
                log.warn("Value of '" + APIConstants.API_STORE_APIS_PER_PAGE + "' is too low, defaulting to 11");
            }
            maxPaginationLimit = start + pagination + 1;
        }
        // Else if the config is not specified we go with default functionality and load all
        else {
            maxPaginationLimit = Integer.MAX_VALUE;
        }*/

        return Integer.MAX_VALUE;
    }

    protected String addResourceFile(String resourcePath, ResourceFile resourceFile,
            Registry registry, String tenantDomain) throws PersistenceException {
        try {
            Resource thumb = registry.newResource();
            thumb.setContentStream(resourceFile.getContent());
            thumb.setMediaType(resourceFile.getContentType());
            registry.put(resourcePath, thumb);
            if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equalsIgnoreCase(tenantDomain)) {
                return RegistryConstants.PATH_SEPARATOR + "registry" + RegistryConstants.PATH_SEPARATOR + "resource"
                        + RegistryConstants.PATH_SEPARATOR + "_system" + RegistryConstants.PATH_SEPARATOR + "governance"
                        + resourcePath;
            } else {
                return "/t/" + tenantDomain + RegistryConstants.PATH_SEPARATOR + "registry"
                        + RegistryConstants.PATH_SEPARATOR + "resource" + RegistryConstants.PATH_SEPARATOR + "_system"
                        + RegistryConstants.PATH_SEPARATOR + "governance" + resourcePath;
            }
        } catch (RegistryException e) {
            String msg = "Error while adding the resource to the registry";
            throw new PersistenceException(msg, e);
        }
    }
    
    class RegistryHolder {
        private Registry registry;
        private boolean isTenantFlowStarted;
        private int tenantId;
        private String registryUser;

        public Registry getRegistry() {
            return registry;
        }

        public void setRegistry(Registry registry) {
            this.registry = registry;
        }

        public boolean isTenantFlowStarted() {
            return isTenantFlowStarted;
        }

        public void setTenantFlowStarted(boolean isTenantFlowStarted) {
            this.isTenantFlowStarted = isTenantFlowStarted;
        }

        public int getTenantId() {
            return tenantId;
        }

        public void setTenantId(int tenantId) {
            this.tenantId = tenantId;
        }

        public String getRegistryUser() {
            return registryUser;
        }

        public void setRegistryUser(String registryUser) {
            this.registryUser = registryUser;
        }
    }
    
    protected RegistryHolder getRegistry(String requestedTenantDomain) throws APIPersistenceException {
        // String username = getTenantAwareUsername(CarbonContext.getThreadLocalCarbonContext().getUsername());
        String tenantAwareUserName = getTenantAwareUsername(username);
        String userTenantDomain = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
        log.debug("Accessing registry for user:" + tenantAwareUserName + " in tenant domain " + userTenantDomain
                + ". Requested tenant domain: " + requestedTenantDomain);
        boolean tenantFlowStarted = false;
        Registry registry;
        RegistryHolder holder = new RegistryHolder();
        holder.setRegistryUser(tenantAwareUserName);
        try {
            if (requestedTenantDomain != null) {
                int id = getTenantManager().getTenantId(requestedTenantDomain);
                RegistryPersistenceUtil.startTenantFlow(requestedTenantDomain);
                tenantFlowStarted = true;
                if (APIConstants.WSO2_ANONYMOUS_USER.equals(tenantAwareUserName)) { // annonymous
                    log.debug("Annonymous user from tenant " + userTenantDomain + " accessing the registry");
                    loadTenantRegistry(id);
                    registry = getRegistryService().getGovernanceUserRegistry(tenantAwareUserName, id);
                    holder.setTenantId(id);
                } else if (userTenantDomain != null && !userTenantDomain.equals(requestedTenantDomain)) { // cross
                                                                                                          // tenant
                                                                                                          // scenario
                    log.debug("Cross tenant user from tenant " + userTenantDomain + " accessing "
                            + requestedTenantDomain + " registry");
                    loadTenantRegistry(id);
                    registry = getRegistryService().getGovernanceSystemRegistry(id);
                    holder.setTenantId(id);
                    /*
                     * int requestedTenantId = getTenantManager().getTenantId(requestedTenantDomain);
                     * registry = getRegistryService()
                     * .getGovernanceUserRegistry(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, requestedTenantId);
                     */
                    ServiceReferenceHolder
                            .setUserRealm((ServiceReferenceHolder.getInstance().getRealmService().getBootstrapRealm()));
                } else {
                    log.debug("Same tenant user : " + tenantAwareUserName + " accessing registry of tenant "
                            + userTenantDomain + ":" + tenantId);
                    loadTenantRegistry(tenantId);
                    registry = getRegistryService().getGovernanceUserRegistry(tenantAwareUserName, tenantId);
                    RegistryPersistenceUtil.loadloadTenantAPIRXT(tenantAwareUserName, tenantId);
                    RegistryPersistenceUtil.loadTenantAPIPolicy(tenantAwareUserName, tenantId);
                    holder.setTenantId(tenantId);
                    ServiceReferenceHolder.setUserRealm((UserRealm) (ServiceReferenceHolder.getInstance()
                            .getRealmService().getTenantUserRealm(tenantId)));
                }
            } else {
                log.debug("Same tenant user : " + tenantAwareUserName + " accessing registry of tenant "
                        + userTenantDomain + ":" + tenantId);
                loadTenantRegistry(tenantId);
                registry = getRegistryService().getGovernanceUserRegistry(tenantAwareUserName, tenantId);
                RegistryPersistenceUtil.loadloadTenantAPIRXT(tenantAwareUserName, tenantId);
                RegistryPersistenceUtil.loadTenantAPIPolicy(tenantAwareUserName, tenantId);
                ServiceReferenceHolder.setUserRealm((UserRealm) (ServiceReferenceHolder.getInstance().getRealmService()
                        .getTenantUserRealm(tenantId)));
                holder.setTenantId(tenantId);
            }
        } catch (RegistryException | UserStoreException | APIManagementException e) {
            String msg = "Failed to get API";
            throw new APIPersistenceException(msg, e);
        }
        holder.setRegistry(registry);
        holder.setTenantFlowStarted(tenantFlowStarted);
        return holder;
    }
    
    private BasicAPI getbasicAPIInfo(String uuid, Registry registry)
            throws APIPersistenceException, GovernanceException {
        BasicAPI api = new BasicAPI();
        GenericArtifactManager apiArtifactManager = RegistryPersistenceUtil.getArtifactManager(registry,
                APIConstants.API_KEY);

        GenericArtifact apiArtifact = apiArtifactManager.getGenericArtifact(uuid);
        if (apiArtifact == null) {
            return null;
        }
        String apiProviderName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
        api.apiProvider = RegistryPersistenceUtil.replaceEmailDomain(apiProviderName);
        api.apiName = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
        api.apiVersion = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
        String visibleRolesList = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES);
        if (visibleRolesList != null) {
            api.visibleRoles = visibleRolesList.split(",");
        }
        api.visibility = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY);

        return api;
    }

    private class BasicAPI {
        String apiName;
        String apiVersion;
        String apiProvider;
        String visibility;
        String[] visibleRoles;
    }

}
