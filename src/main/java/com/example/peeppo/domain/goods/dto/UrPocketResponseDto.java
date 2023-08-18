package com.example.peeppo.domain.goods.dto;

import com.example.peeppo.domain.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UrPocketResponseDto {

    private Long userId;
    private String nickName;
    private String getUserImg;
    private String location;
    private List<GoodsListResponseDto> goodsListResponseDto;

    public UrPocketResponseDto(User user, List<GoodsListResponseDto> goodsListResponseDto) {
        this.userId = user.getUserId();
        this.nickName = user.getNickname();
        this.getUserImg = user.getUserImg();
        this.location = user.getLocation();
        this.goodsListResponseDto = goodsListResponseDto;
    }
}
