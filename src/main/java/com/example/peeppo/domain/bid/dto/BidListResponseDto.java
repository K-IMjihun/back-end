package com.example.peeppo.domain.bid.dto;

import com.example.peeppo.domain.bid.entity.Bid;
import com.example.peeppo.domain.bid.enums.BidStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class BidListResponseDto {
    private Long bidId;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private String goodsImg;
    private String title;
    private String location;
    private BidStatus bidStatus;

    public BidListResponseDto(Bid bid, String goodsImg) {
        this.bidId = bid.getBidId();
        this.createdAt = bid.getCreatedAt();
        this.modifiedAt = bid.getModifiedAt();
        this.goodsImg = goodsImg;
        this.title = bid.getGoods().getTitle();
        this.location = bid.getUser().getLocation();
        this.bidStatus = bid.getBidStatus();
    }
}
