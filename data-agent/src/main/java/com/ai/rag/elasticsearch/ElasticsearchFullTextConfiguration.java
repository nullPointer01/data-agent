package com.ai.rag.elasticsearch;
import com.ai.rag.elasticsearch.ElasticsearchProperties;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * Elasticsearch 全文检索 Bean 配置，基于官方 Java SDK。
 *
 * @author data-agent
 */
@Configuration
@ConditionalOnProperty(name = "app.rag.full-text-provider", havingValue = "elasticsearch")
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchFullTextConfiguration {

    @Bean
    public RestClient elasticsearchRestClient(ElasticsearchProperties properties) {
        URI uri = URI.create(properties.getBaseUrl());
        HttpHost host = new HttpHost(uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 9200,
                uri.getScheme() != null ? uri.getScheme() : "http");

        RestClientBuilder builder = RestClient.builder(host)
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout((int) properties.getConnectTimeout().toMillis())
                        .setSocketTimeout((int) properties.getRequestTimeout().toMillis()));

        if (StringUtils.hasText(properties.getUsername())) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(httpClient ->
                    httpClient.setDefaultCredentialsProvider(credentialsProvider));
        }

        return builder.build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient, ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    @Bean
    public ElasticsearchFullTextClient elasticsearchFullTextClient(ElasticsearchClient esClient,
            ObjectMapper objectMapper, ElasticsearchProperties properties) {
        return new ElasticsearchFullTextClient(esClient, objectMapper, properties);
    }
}
