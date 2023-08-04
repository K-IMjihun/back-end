
package com.example.peeppo.global.config.redis;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory(){
        RedisStandaloneConfiguration redisStandaloneConfiguration
                = new RedisStandaloneConfiguration(redisHost,redisPort);

        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }
    @Bean
    //redismessageListenercontainer 는 Redis channel(Topic)으로 부터 메시지를 받고,
    //주입된 리스너들에게 비동기적으로 dispatch하는 역할을 수행하는 컨테이너이다
    // 즉, redis에 발행된 (pub)된 메시지 처리를 위한 리스너 설정
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       MessageListenerAdapter listenerAdapter,
                                                                       ChannelTopic channelTopic){
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, channelTopic);
        return container;
    }

    @Bean
    //위의 컨테이너로부터 메시지를 dispatch 받고 실제 메시지를 처리하는 로직인 subscriber bean을 추가해준다
    // 메시지를 구독자에게 보내는 역할
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber){
        return new MessageListenerAdapter(subscriber, "sendMessage");
    }

    //redis서버와 상호작용하기 위한 템플릿 관련 설정, redis 서버에는 bytes 코드만이 저장되므로
    //key와 value에 serializer을 설정해준다
    @Bean
    public RedisTemplate<String, Object> redisTemplate
            (RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(String.class)); //Json포맷 형식으로 메시지를 교환하기 위해
        return redisTemplate;
    }

    //Topic 공유를 위해 Channel Topic을 빈으로 등록해 단일화 시켜준다.
    @Bean
    public ChannelTopic channelTopic() {
        return new ChannelTopic("chatroom");
    }
}
