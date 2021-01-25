package org.wso2.carbon.graphql.api.devportal;

import org.wso2.carbon.graphql.api.devportal.data.*;
import org.wso2.carbon.graphql.api.devportal.data.tagData.TagData;
import org.wso2.carbon.graphql.api.devportal.modules.*;
import graphql.schema.DataFetcher;
import org.springframework.stereotype.Component;

@Component
public class ApiService {

    ApiDetails apiDetails = new ApiDetails();
    BusinessInformationData dummyBusinessInformation = new BusinessInformationData();
    APIEndpointURLsData apiEndpointURLData = new APIEndpointURLsData();
    APIUrlsData dummyAPIUrlsDTO = new APIUrlsData();
    DefaultAPIURLsData defaultAPIURLsData = new DefaultAPIURLsData();
    PaginationData paginationData = new PaginationData();

    TierData tierData = new TierData();
    MonetizationLabelData monetizationLabelData = new MonetizationLabelData();
    AdvertiseData advertiseData = new AdvertiseData();
    ScopesData scopesData = new ScopesData();
    OperationData operationData = new OperationData();

    IngressUrlsData ingressUrlsData  = new IngressUrlsData();

    LabelData labelData = new LabelData();

    APIDTOData apidtoData = new APIDTOData();

    ArtifactData artifactData = new ArtifactData();


    public DataFetcher getApiType(){
        return env->{
            Api api = env.getSource();
            return apidtoData.getApiType(api.getId());

        };
    }
    public DataFetcher getApiCreatedTime(){
        return env->{
            Api api = env.getSource();
            return apidtoData.getApiCreatedTime(api.getId());

        };
    }
    public DataFetcher getApiUpdatedTime(){
        return env->{
            Api api = env.getSource();
            return apidtoData.getApiLastUpdateTime(api.getId());

        };
    }

    public DataFetcher getApiDefinition(){
        return env->{
            Api api = env.getSource();
            return apidtoData.getApiDefinition(api.getId());

        };
    }

    public DataFetcher getApisFromArtifact(){


        return env-> apiDetails.getAllApis();

    }


    public DataFetcher getApiFromArtifact(){
        return env->{
            String Id = env.getArgument("id");
            return apiDetails.getApi(Id);

        };
    }
    public DataFetcher getApiCount(){
        return env-> artifactData.apiCount();
    }

    public DataFetcher  getApiRating(){
        return env->{
            Api api = env.getSource();
            return apiDetails.getApiRating(api.getId());
        };
    }

    public DataFetcher getTierNames(){
        return env->{
            Api api = env.getSource();
            return tierData.getTierName(api.getId());
        };
    }

    public DataFetcher getTierDetails(){
        return env->{
            TierNameDTO tierNameDTO = env.getSource();
            return tierData.getTierData(tierNameDTO.getApiId(),tierNameDTO.getName());
        };
    }

    public DataFetcher getMonetizationLabel(){
        return env->{
            Api api = env.getSource();
            return monetizationLabelData.getMonetizationLabelData(api.getId());
        };
    }



    public DataFetcher getOperationInformation(){
        return env->{
            Api api = env.getSource();
            return operationData.getOperationData(api.getId());

        };
    }
    public DataFetcher getBusinessInformation(){

        return env-> {
            Api api = env.getSource();
            return dummyBusinessInformation.getBusinessInformations(api.getId());
        };
    }

    public DataFetcher getLabelInformation(){
        return env->{
          Api api = env.getSource();
          return labelData.getLabelNames(api.getId());
        };
    }
    public DataFetcher getLabelsDetails(){
        return env->{
            LabelNameDTO labelNameDTO = env.getSource();
            return labelData.getLabeldata(labelNameDTO.getId(),labelNameDTO.getName());
        };
    }
    public DataFetcher getScopeInformation(){
        return env->{
            Api api = env.getSource();
            return scopesData.getScopesData(api.getId());
        };
    }

    public DataFetcher getAdvertiseInformation(){
        return env->{
            Api api = env.getSource();
            return advertiseData.getAdvertiseInformation(api.getId());
        };
    }

    public DataFetcher getApiUrlsEndPoint(){
        return env->{
            Api api = env.getSource();
            return apiEndpointURLData.apiEndpointURLsDTO(api.getId());

        };
    }
    public DataFetcher getApiUrlsDTO(){
        return env ->{
            APIEndpointURLsDTO apiEndpointURLsDTO = env.getSource();
            return dummyAPIUrlsDTO.apiURLsDTO(apiEndpointURLsDTO.getApiId());

        };
    }

    public DataFetcher getDefaultApiUrlsDTO(){
        return env->{
            APIEndpointURLsDTO apiEndpointURLsDTO = env.getSource();
            return defaultAPIURLsData.getDefaultAPIURLsData(apiEndpointURLsDTO.getApiId());
        };

    }

    public DataFetcher getPagination(){
        return env->paginationData.getPaginationData();
    }

    public DataFetcher getDeploymentEnvironmentName(){
        return env->{
            Api api = env.getSource();
            return ingressUrlsData.getDeplymentEnvironmentName(api.getId());
        };
    }
    public DataFetcher getDeploymentClusterInformation(){
        return env ->{
            Api api = env.getSource();
            return ingressUrlsData.getDeploymentClusterData(api.getId());
        };
    }



}
