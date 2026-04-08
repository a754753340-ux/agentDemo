package io.agentscope.examples.monolithchat.conf;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfig {

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient(ElasticsearchMemoryProperties properties) {
        RestClientBuilder builder = RestClient.builder(new HttpHost(properties.getHost(), properties.getPort(), "http"));
        if (properties.getUserName() != null && !properties.getUserName().isBlank()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUserName(), properties.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return new RestHighLevelClient(builder);
    }
}
