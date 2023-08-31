package com.example.peeppo.domain.bid.service;

import com.example.peeppo.domain.auction.dto.TimeRemaining;
import com.example.peeppo.domain.auction.entity.Auction;
import com.example.peeppo.domain.auction.repository.AuctionRepository;
import com.example.peeppo.domain.bid.dto.BidGoodsListRequestDto;
import com.example.peeppo.domain.bid.dto.BidListResponseDto;
import com.example.peeppo.domain.bid.dto.BidTradeListResponseDto;
import com.example.peeppo.domain.bid.dto.ChoiceRequestDto;
import com.example.peeppo.domain.bid.entity.Bid;
import com.example.peeppo.domain.bid.entity.Choice;
import com.example.peeppo.domain.bid.enums.BidStatus;
import com.example.peeppo.domain.bid.repository.BidRepository;
import com.example.peeppo.domain.bid.repository.ChoiceBidRepository;
import com.example.peeppo.domain.bid.repository.QueryRepository;
import com.example.peeppo.domain.goods.entity.Goods;
import com.example.peeppo.domain.goods.enums.GoodsStatus;
import com.example.peeppo.domain.goods.repository.goods.GoodsRepository;
import com.example.peeppo.domain.image.repository.ImageRepository;
import com.example.peeppo.domain.notification.entity.Notification;
import com.example.peeppo.domain.notification.repository.NotificationRepository;
import com.example.peeppo.domain.rating.entity.RatingGoods;
import com.example.peeppo.domain.rating.repository.ratingGoodsRepository.RatingGoodsRepository;
import com.example.peeppo.domain.user.dto.ResponseDto;
import com.example.peeppo.domain.user.entity.User;
import com.example.peeppo.domain.user.repository.UserRepository;
import com.example.peeppo.global.responseDto.ApiResponse;
import com.example.peeppo.global.responseDto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final GoodsRepository goodsRepository;
    private final ImageRepository imageRepository;
    private final QueryRepository queryRepository;
    private final ChoiceBidRepository choiceBidRepository;
    private final RatingGoodsRepository ratingGoodsRepository;
    private final NotificationRepository notificationRepository;

    public ResponseDto bidding(User user, Long auctionId, BidGoodsListRequestDto bidGoodsListRequestDto) throws IllegalAccessException {

        Auction auction = getAuction(auctionId);
        List<Bid> List = new ArrayList<>();
        Double totalPrice = 0D;

        for (Long goodsId : bidGoodsListRequestDto.getGoodsId()){
            RatingGoods ratingGoods = ratingGoodsRepository.findByGoodsGoodsId(goodsId);
            totalPrice += ratingGoods.getAvgRatingPrice();
        }

        //경매 진행 여부
        if (!auction.getGoods().getGoodsStatus().equals(GoodsStatus.ONAUCTION) ||
                auction.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalAccessException("본인의 경매물품엔 입찰하실 수 없습니다.");
        }
        for (Long goodsId : bidGoodsListRequestDto.getGoodsId()) {
            Goods goods = getGoods(goodsId);
            String goodsImg = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goodsId).getImageUrl();

            if (goods.getIsDeleted() && !goods.getUser().getUserId().equals(user.getUserId())) {
                System.out.println(" ");
                throw new IllegalAccessException();
                //여기도 고민
            }
            if (goods.getGoodsStatus().equals(GoodsStatus.ONSALE) &&
                    totalPrice > auction.getLowPrice()) {
                //시작가보다 낮을 경우
                List.add(new Bid(user, auction, goods, goodsImg));
                goods.changeStatus(GoodsStatus.BIDDING);
            } else {
                System.out.println("3 ");
                throw new IllegalAccessException();
            }
        }

        Notification notification = notificationRepository.findByUserUserId(auction.getUser().getUserId());

        if (notification == null) {
            notification = new Notification();
            notification.setUser(user);
        }

        notification.setIsAuction(false);
        notification.updateAuctionCount();
        notification.Checked(false);

        notificationRepository.save(notification);

        bidRepository.saveAll(List);

        return new ResponseDto("입찰이 완료되었습니다.", HttpStatus.OK.value(), "OK");
    }

    public ApiResponse<Page<BidListResponseDto>> BidList(Long auctionId, int page) {
        Pageable pageable = PageRequest.of(page, 8);
        Page<Bid> bidPage = bidRepository.findSortedBySellersPick(auctionId, pageable);
        List<BidListResponseDto> bidListResponseList = bidPage
                .stream()
                .map(bid -> {
                    String goodsImg = imageRepository
                            .findByGoodsGoodsIdOrderByCreatedAtAscFirst(bid.getGoods().getGoodsId())
                            .getImageUrl();
                    return new BidListResponseDto(bid, goodsImg);
                })
                .collect(Collectors.toList());

        Page<BidListResponseDto> bidPageResponse = new PageImpl<>(bidListResponseList, pageable, bidPage.getTotalElements());
        return new ApiResponse<>(true, bidPageResponse, null);
    }

    //경매자가 선택
    public ResponseDto choiceBids(User user, Long auctionId, ChoiceRequestDto choiceRequestDto) throws IllegalAccessException {
        Auction auction = getAuction(auctionId);
        if (!auction.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalAccessException("잘못된 접근입니다. 다시 시도해주세요.");
        }
        List<Bid> bidList = new ArrayList<>();
            for (Long bidId : choiceRequestDto.getBidId()) {
                Bid bid = bidRepository.findById(bidId)
                        .orElseThrow(() -> new NullPointerException("존재하지 않는 입찰품입니다."));
                bid.select();
                bidList.add(bid);
            }
            bidRepository.saveAll(bidList);
        return new ResponseDto("선택이 완료되었습니다.", HttpStatus.OK.value(), "OK");
    }

    //경매자가 선택 바꾸는 기능
    public ResponseDto choiceUpdateBids(User user, Long auctionId, ChoiceRequestDto choiceRequestDto) throws IllegalAccessException {
        Auction auction = getAuction(auctionId);
        List<Choice> bidsList = new ArrayList<>();
        List<Choice> findAllChoice = queryRepository.findChoice(auctionId);

        if (auction.getUser().getUserId().equals(user.getUserId())) {
            for (Choice choice : findAllChoice) {
                choiceBidRepository.delete(choice);
            }

            getBiddingList(choiceRequestDto, auction, bidsList);
        } else {
            throw new IllegalAccessException();
        }

        return new ResponseDto("재선택 되었습니다.", HttpStatus.OK.value(), "OK");
    }

    private void getBiddingList(ChoiceRequestDto choiceRequestDto, Auction auction, List<Choice> bidsList) throws IllegalAccessException {
        for (Long bidId : choiceRequestDto.getBidId()) {
            Bid bid = getBid(bidId);

            if (!auction.getAuctionId().equals(bid.getAuction().getAuctionId())) {
                throw new IllegalAccessException();
            }
            bidsList.add(new Choice(bid, auction));
        }

        choiceBidRepository.saveAll(bidsList);
    }

    public ResponseEntity<Page<BidTradeListResponseDto>> bidTradeList(User user, int page, int size, String sortBy, boolean isAsc,
                                                                      BidStatus bidStatus) {
        Pageable pageable = paging(page, size, sortBy, isAsc);
        Page<Auction> myAuctionPage = null;

        if (bidStatus != null) {
            Bid bid = bidRepository.findByUserUserIdAndBidStatus(user.getUserId(), bidStatus);
            Long auctionId = bidRepository.findAuctionIdByBidId(bid.getBidId());
            myAuctionPage = auctionRepository.findByAuctionId(auctionId, pageable);
        } else {
            Bid bid = bidRepository.findByUserUserIdAndBidStatusIsNotNull(user.getUserId());
            Long auctionId = bidRepository.findAuctionIdByBidId(bid.getBidId());
            myAuctionPage = auctionRepository.findByAuctionId(auctionId, pageable);
        }

        List<BidTradeListResponseDto> auctionResponseDtoList = myAuctionPage.stream()
                .map(auction -> {
                    TimeRemaining timeRemaining = countDownTime(auction);
                    Long bidCount = findBidCount(auction.getAuctionId());
                    Bid bid = bidRepository.findByAuctionAuctionIdAndUserUserId(auction.getAuctionId(), user.getUserId());
                    return new BidTradeListResponseDto(auction, timeRemaining, bidCount, bid);
                })
                .collect(Collectors.toList());

        PageResponse response = new PageResponse<>(auctionResponseDtoList, pageable, myAuctionPage.getTotalElements());
        return ResponseEntity.status(HttpStatus.OK.value()).body(response);
    }

    public Long findBidCount(Long id) {
        return bidRepository.countByAuctionAuctionId(id);
    }

    public TimeRemaining countDownTime(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        long days = ChronoUnit.DAYS.between(now, auction.getAuctionEndTime());
        long hours = ChronoUnit.HOURS.between(now, auction.getAuctionEndTime());
        long minutes = ChronoUnit.MINUTES.between(now, auction.getAuctionEndTime());
        long seconds = ChronoUnit.SECONDS.between(now, auction.getAuctionEndTime());

        return new TimeRemaining(days, hours % 24, minutes % 60, seconds % 60);
    }

    private Bid getBid(Long bidId) {
        return bidRepository.findById(bidId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 bidId 입니다."));
    }

    private Choice getChoice(Long choiceId) {
        return choiceBidRepository.findById(choiceId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 choiceId 입니다."));
    }

    private Auction getAuction(Long auctionId) {
        return auctionRepository.findById(auctionId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 auctionId 입니다."));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 userId 입니다."));
    }

    private Goods getGoods(Long goodsId) {
        return goodsRepository.findById(goodsId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 goodsId 입니다."));
    }

    private Pageable paging(int page, int size, String sortBy, boolean isAsc) {
        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        return PageRequest.of(page, size, sort);
    }
}
