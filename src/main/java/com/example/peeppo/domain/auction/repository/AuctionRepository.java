package com.example.peeppo.domain.auction.repository;

import com.example.peeppo.domain.auction.entity.Auction;
import com.example.peeppo.domain.auction.enums.AuctionStatus;
import com.example.peeppo.domain.goods.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long>{
    Page<Auction> findByUserUserIdAndAuctionStatus(Long userId, Pageable pageable, AuctionStatus auctionStatus);


    @Query(value = "select a.* from auction a inner join (SELECT b.auction_id, COUNT(b.bid_id) AS bidCount FROM bid b GROUP BY b.auction_id ORDER BY bidCount DESC LIMIT 3) as top3auction on a.auction_id= top3auction.auction_id", nativeQuery = true)
    List<Auction> findTop3Auction();

    Page<Auction> findByUserUserId(Long userId, Pageable pageable);

    Page<Auction> findByAuctionId(Long auctionId, Pageable pageable);

    Page<Auction> findByGoodsCategory(Category category, Pageable pageable);
}