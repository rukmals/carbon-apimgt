package org.wso2.carbon.graphql.api.devportal.data;

import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.graphql.api.devportal.modules.APIDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class APIDTOData {
    public static final String GET_API_DATA = "SELECT * FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_NAME = "SELECT API.API_NAME FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_CONTEXT = "SELECT API.CONTEXT FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_VERSION = "SELECT API.API_VERSION FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_PROVIDER = "SELECT API.API_PROVIDER FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_TYPE = "SELECT API.API_TYPE FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_CREATED_TIME = "SELECT API.CREATED_TIME FROM AM_API API WHERE API.API_UUID = ? ";
    public static final String GET_API_UPDATED_TIME = "SELECT API.UPDATED_TIME FROM AM_API API WHERE API.API_UUID = ? ";

//    public APIDTO getApiData(String Id){
//        //List<APIDTO> apidtos = new ArrayList<>();
//
//        String uuid = null;
//        String apiName = null;
//        String context= null;
//        String version= null;
//        String provider= null;
//        String type= null;
//        String createdTime= null;
//        String lastUpdate= null;
//        try (Connection connection = APIMgtDBUtil.getConnection();
//             PreparedStatement statement = connection.prepareStatement(GET_API_DATA)) {
//             statement.setString(1, Id);
//
//            ResultSet resultSet = statement.executeQuery();
//
//            while (resultSet.next()) {
//                uuid = resultSet.getString("API_UUID");
//                 apiName = resultSet.getString("API_NAME");
//                 context = resultSet.getString("CONTEXT");
//                 version = resultSet.getString("API_VERSION");
//                 provider = resultSet.getString("API_PROVIDER");
//                 type = resultSet.getString("API_TYPE");
//                 createdTime = resultSet.getString("CREATED_TIME");
//                 lastUpdate = resultSet.getString("UPDATED_TIME");
//
//
//            }
//        } catch (SQLException e) {
//
//        }
//
//        return new APIDTO(uuid,apiName,context,version,provider,type,createdTime,lastUpdate);
//
//    }

    public String getApiName(String Id){
        String apiName = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_NAME)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                apiName = resultSet.getString("API_NAME");
            }
        } catch (SQLException e) {

        }
        return apiName;
    }
    public String getApiContext(String Id){
        String Context = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_CONTEXT)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                Context = resultSet.getString("CONTEXT");
            }
        } catch (SQLException e) {

        }
        return Context;
    }
    public String getApiVersion(String Id){
        String version = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_VERSION)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                version = resultSet.getString("API_VERSION");
            }
        } catch (SQLException e) {

        }
        return version;
    }
    public String getApiProvider(String Id){
        String provider = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_PROVIDER)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                provider = resultSet.getString("API_PROVIDER");
            }
        } catch (SQLException e) {

        }
        return provider;
    }
    public String getApiType(String Id){
        String type = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_TYPE)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                type = resultSet.getString("API_TYPE");
            }
        } catch (SQLException e) {

        }
        return type;
    }
    public String getApiCreatedTime(String Id){
        String createdTime = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_CREATED_TIME)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                createdTime = resultSet.getString("CREATED_TIME");
            }
        } catch (SQLException e) {

        }
        return createdTime;
    }
    public String getApiLastUpdateTime(String Id){
        String lastUpdate = null;
        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_API_UPDATED_TIME)) {
            statement.setString(1, Id);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                lastUpdate = resultSet.getString("UPDATED_TIME");
            }
        } catch (SQLException e) {

        }
        return lastUpdate;
    }
}
