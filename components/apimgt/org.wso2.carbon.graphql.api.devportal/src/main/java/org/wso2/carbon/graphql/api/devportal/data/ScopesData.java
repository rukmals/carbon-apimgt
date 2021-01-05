package org.wso2.carbon.graphql.api.devportal.data;

import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.ExceptionCodes;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.dao.constants.SQLConstants;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.persistence.APIConstants;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.graphql.api.devportal.ArtifactData;
import org.wso2.carbon.graphql.api.devportal.modules.ScopesDTO;
import org.wso2.carbon.graphql.api.devportal.RegistryData;
import org.wso2.carbon.apimgt.api.model.ApiTypeWrapper;
import org.wso2.carbon.apimgt.api.model.Scope;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.wso2.carbon.apimgt.impl.utils.APIUtil.getAPIScopes;
import static org.wso2.carbon.apimgt.persistence.utils.PersistenceUtil.replaceEmailDomainBack;

public class ScopesData {

    public List<ScopesDTO> getScopesData(String Id) throws APIManagementException{

        ArtifactData artifactData = new ArtifactData();

        List<String> identifierParams = artifactData.getApiIdentifireParams(Id);



        String apiVersion = identifierParams.get(2);//artifactData.getDevportalApis(Id).getAttribute(APIConstants.API_OVERVIEW_VERSION);
        String providerName = identifierParams.get(1);//artifactData.getDevportalApis(Id).getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
        String apiName = identifierParams.get(0);//artifactData.getDevportalApis(Id).getAttribute(APIConstants.API_OVERVIEW_NAME);

        APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
        String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));


        Map<String, Scope> scopeToKeyMapping = getAPIScopes(apiIdentifier, tenantDomainName);
        Set<Scope> scopes = new LinkedHashSet<>(scopeToKeyMapping.values());


        List<Scope> scopeList = new ArrayList<>(scopes);

        List<ScopesDTO> scopeData = new ArrayList<ScopesDTO>();

        for (int i=0 ; i<scopeList.size();i++){
            String key = scopeList.get(i).getKey();
            String name = scopeList.get(i).getName();
            String role = scopeList.get(i).getRoles();
            String description = scopeList.get(i).getDescription();

            scopeData.add(new ScopesDTO(key,name,role,description));
        }

        return  scopeData;
    }
}