package org.wso2.carbon.graphql.api.devportal.data;

import org.wso2.carbon.apimgt.api.APIConsumer;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.persistence.APIConstants;
import org.wso2.carbon.apimgt.persistence.dto.DevPortalAPI;
import org.wso2.carbon.apimgt.persistence.exceptions.APIPersistenceException;
import org.wso2.carbon.apimgt.rest.api.common.RestApiCommonUtil;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.graphql.api.devportal.ArtifactData;
import org.wso2.carbon.graphql.api.devportal.modules.LabelDTO;
import org.wso2.carbon.graphql.api.devportal.RegistryData;
import org.wso2.carbon.apimgt.api.model.ApiTypeWrapper;
import org.wso2.carbon.apimgt.api.model.Label;
import org.wso2.carbon.graphql.api.devportal.modules.LabelNameDTO;
import org.wso2.carbon.graphql.api.devportal.modules.TierNameDTO;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class LabelData {

    public List<LabelDTO> getLabeldata(String name) throws APIManagementException {


       //List<Label> labels = ApiMgtDAO.getInstance().getAllLabels(MultitenantUtils.getTenantDomain("wso2.anonymous.user"));//getLabelsFromAPIGovernanceArtifact(apiArtifact,apiIdentifier.getProviderName());

        String username = "wso2.anonymous.user";
        APIConsumer apiConsumer = RestApiCommonUtil.getConsumer(username);
        List<Label> labels = apiConsumer.getLabelDataFromDAO();


        List<LabelDTO> labelData = new ArrayList<LabelDTO>();

        for (int i = 0; i<labels.size();i++) {
            // String name = labelset.toString();
            if(name.equals(labels.get(i).getName())){
                String description = labels.get(i).getDescription();
                List<String> accessUrlsList = labels.get(i).getAccessUrls();
                String accessUrls = accessUrlsList.toString();

                labelData.add(new LabelDTO(name,description,accessUrls));
            }


        }
        return labelData;
    }

    public List<LabelNameDTO> getLabelNames(String Id) throws APIPersistenceException {
        ArtifactData artifactData = new ArtifactData();
        DevPortalAPI devPortalAPI = artifactData.getApiFromUUID(Id);
        Set<String> labels = devPortalAPI.getGatewayLabels();


        List<LabelNameDTO> labelNameDTOList = new ArrayList<>();
        for (String labelName : labels) {
            labelNameDTOList.add(new LabelNameDTO(devPortalAPI.getId(),labelName));
        }

        return labelNameDTOList;
    }
//    public List<LabelNameDTO> getLabelNamesForApiListing(DevPortalAPI devPortalAPI) throws APIPersistenceException {
//        ArtifactData artifactData = new ArtifactData();
//        DevPortalAPI devPortalAPI = artifactData.getApiFromUUID(Id);
//        Set<String> labels = devPortalAPI.getGatewayLabels();
//
//
//        List<LabelNameDTO> labelNameDTOList = new ArrayList<>();
//        for (String labelName : labels) {
//            labelNameDTOList.add(new LabelNameDTO(devPortalAPI.getId(),labelName));
//        }
//
//        return labelNameDTOList;
//    }
}
