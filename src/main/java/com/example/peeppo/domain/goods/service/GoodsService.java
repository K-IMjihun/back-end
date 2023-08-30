package com.example.peeppo.domain.goods.service;

import com.amazonaws.services.s3.AmazonS3;
import com.example.peeppo.domain.auction.dto.TestListResponseDto;
import com.example.peeppo.domain.auction.dto.TimeRemaining;
import com.example.peeppo.domain.auction.entity.Auction;
import com.example.peeppo.domain.bid.entity.Bid;
import com.example.peeppo.domain.dibs.entity.Dibs;
import com.example.peeppo.domain.dibs.repository.DibsRepository;
import com.example.peeppo.domain.dibs.service.DibsService;
import com.example.peeppo.domain.goods.entity.RequestGoods;
import com.example.peeppo.domain.goods.enums.GoodsStatus;
import com.example.peeppo.domain.goods.dto.*;
import com.example.peeppo.domain.goods.entity.Goods;
import com.example.peeppo.domain.goods.entity.WantedGoods;
import com.example.peeppo.domain.goods.enums.RequestStatus;
import com.example.peeppo.domain.goods.enums.RequestedStatus;
import com.example.peeppo.domain.goods.repository.GoodsRepository;
import com.example.peeppo.domain.goods.repository.RequestRepository;
import com.example.peeppo.domain.goods.repository.WantedGoodsRepository;
import com.example.peeppo.domain.image.entity.Image;
import com.example.peeppo.domain.image.helper.ImageHelper;
import com.example.peeppo.domain.image.repository.ImageRepository;
import com.example.peeppo.domain.rating.entity.RatingGoods;
import com.example.peeppo.domain.rating.helper.RatingHelper;
import com.example.peeppo.domain.rating.repository.ratingGoodsRepository.RatingGoodsRepository;
import com.example.peeppo.domain.user.dto.ResponseDto;
import com.example.peeppo.domain.user.entity.User;
import com.example.peeppo.domain.user.repository.UserRepository;
import com.example.peeppo.global.responseDto.ApiResponse;
import com.example.peeppo.global.responseDto.PageResponse;
import com.example.peeppo.global.security.UserDetailsImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsService {
    private final GoodsRepository goodsRepository;
    private final ImageRepository imageRepository;
    private final WantedGoodsRepository wantedGoodsRepository;
    private final ImageHelper imageHelper;
    private final AmazonS3 amazonS3;
    private final String bucket;
    private final UserRepository userRepository;
    private final RatingGoodsRepository ratingGoodsRepository;
    private final RequestRepository requestRepository;

    private final RatingHelper ratingHelper;
    private final DibsService dibsService;

    private final DibsRepository dibsRepository;
    private static final String RECENT_GOODS = "goods";
    private static final int MAX_RECENT_GOODS = 4;
    //private List<Long> goodsRecent = new ArrayList<>();
    private List<String> goodsRecent = new ArrayList<>();

    @Transactional
    public ApiResponse<GoodsResponseDto> goodsCreate(GoodsRequestDto goodsRequestDto,
                                                     List<MultipartFile> images,
                                                     WantedRequestDto wantedRequestDto,
                                                     User user) {
        if (goodsRequestDto.getSellerPrice() == null && goodsRequestDto.getRatingCheck() == true) {
            throw new IllegalArgumentException("레이팅을 원하시면 가격을 입력해주세요.");
        }
        WantedGoods wantedGoods = new WantedGoods(wantedRequestDto);
        Goods goods = new Goods(goodsRequestDto, wantedGoods, user, GoodsStatus.ONSALE);
        RatingGoods ratingGoods = new RatingGoods(goods);

        goodsRepository.save(goods);
        ratingGoodsRepository.save(ratingGoods);
        wantedGoodsRepository.save(wantedGoods);

        List<String> imageUrls = imageHelper
                .saveImagesToS3AndRepository(images, amazonS3, bucket, goods)
                .stream()
                .map(Image::getImageUrl)
                .collect(Collectors.toList());

//        ratingHelper.createRating(sellerPriceRequestDto.getSellerPrice(), goods, image);

        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUrls, wantedGoods), null);
    }

    public Page<GoodsListResponseDto> allGoods(int page, int size, String sortBy, boolean isAsc, UserDetailsImpl userDetails) {
        if (userDetails == null) { // 비로그인시
            return allGoodsEveryone(page, size, sortBy, isAsc);
        }
        User user = userDetails.getUser();

        Pageable pageable = paging(page, size, sortBy, isAsc);
        Page<Goods> goodsPage = goodsRepository.findAllByIsDeletedFalse(pageable);
        List<GoodsListResponseDto> goodsResponseList = new ArrayList<>();

        for (Goods goods : goodsPage.getContent()) {
            boolean checkSameUser = Objects.equals(goods.getUser().getUserId(), userDetails.getUser().getUserId());
            Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
            boolean checkDibs = dibsRepository.findByUserUserIdAndGoodsGoodsId(user.getUserId(), goods.getGoodsId())
                    .isPresent();
            goodsResponseList.add(new GoodsListResponseDto(goods, image.getImageUrl(), checkDibs, checkSameUser));
        }

        return new PageResponse<>(goodsResponseList, pageable, goodsPage.getTotalElements());
    }

    public Page<GoodsListResponseDto> allGoodsEveryone(int page, int size, String sortBy, boolean isAsc) {

        Pageable pageable = paging(page, size, sortBy, isAsc);
        Page<Goods> goodsPage = goodsRepository.findAllByIsDeletedFalse(pageable);
        List<GoodsListResponseDto> goodsResponseList = new ArrayList<>();

        for (Goods goods : goodsPage.getContent()) {
            Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
            goodsResponseList.add(new GoodsListResponseDto(goods, image.getImageUrl()));
        }

        return new PageResponse<>(goodsResponseList, pageable, goodsPage.getTotalElements());
    }


    public ApiResponse<GoodsResponseDto> getGoods(Long goodsId, User user) {
        Goods goods = findGoods(goodsId);
        boolean checkSameUser = goods.getUser().getUserId() == user.getUserId();
        WantedGoods wantedGoods = findWantedGoods(goodsId);
        List<String> imageUrls = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAsc(goodsId)
                .stream()
                .map(Image::getImageUrl)
                .collect(Collectors.toList());

        if (goodsRecent.size() >= MAX_RECENT_GOODS) {
            goodsRecent.remove(0);
        }
        // goodsRecent.add(Long.toString(goods.getGoodsId())); // 조회시에 리스트에 추가 !
        Optional<Dibs> dibsGoods = dibsRepository.findByUserUserIdAndGoodsGoodsId(user.getUserId(), goodsId);
        boolean checkDibs = dibsGoods.isPresent();

        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUrls, wantedGoods, checkSameUser, checkDibs), null);
    }

    public User findUserId(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }


    public ApiResponse<PocketResponseDto> getMyGoods(int page,
                                                     int size,
                                                     String sortBy,
                                                     boolean isAsc,
                                                     Long userId) {

        Pageable pageable = paging(page, size, sortBy, isAsc);
        User user = findUserId(userId);
        Page<Goods> goodsList = goodsRepository.findAllByUserAndIsDeletedFalse(user, pageable);

        List<PocketListResponseDto> myGoods = goodsList.stream()
                .map(goods -> {
                    long ratingPrice = (long) ratingHelper.getAvgPriceByGoodsId(goods.getGoodsId());
                    Image firstImage = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
                    return new PocketListResponseDto(goods, firstImage.getImageUrl(), ratingPrice);
                }).collect(Collectors.toList());

        return new ApiResponse<>(true, new PocketResponseDto(user,
                new PageImpl<>(myGoods, pageable, goodsList.getTotalElements())), null);
    }

    @Transactional
    public ApiResponse<GoodsResponseDto> goodsUpdate(Long goodsId, GoodsRequestDto goodsRequestDto, List<MultipartFile> images, WantedRequestDto wantedRequestDto) {
        Goods goods = findGoods(goodsId);
        WantedGoods wantedGoods = findWantedGoods(goodsId);

        // repository 이미지 삭제
        List<Image> imageList = imageRepository.findByGoodsGoodsId(goodsId);
        imageHelper.repositoryImageDelete(imageList);

        // s3 이미지 삭제
        for (Image image : imageList) {
            imageHelper.deleteFileFromS3(image.getImageKey(), amazonS3, bucket);
        }

        // 이미지 업로드
        List<String> imageUrls = imageHelper.saveImagesToS3AndRepository(images, amazonS3, bucket, goods)
                .stream()
                .map(Image::getImageUrl)
                .collect(Collectors.toList());
        goods.update(goodsRequestDto);
        wantedGoods.update(wantedRequestDto);

        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUrls, wantedGoods), null);
    }

    @Transactional
    public ApiResponse<DeleteResponseDto> deleteGoods(Long goodsId, Long userId) throws IllegalAccessException {
        Goods goods = findGoods(goodsId);
        if (Objects.equals(userId, goods.getUser().getUserId())) {
            goods.delete();
            goodsRepository.save(goods);
        } else {
            throw new IllegalAccessException();
        }
        return new ApiResponse<>(true, new DeleteResponseDto("삭제되었습니다"), null);
    }

    public Goods findGoods(Long goodsId) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow(() ->
                new NullPointerException("해당 게시글은 존재하지 않습니다."));
        if (goods.getIsDeleted()) {
            throw new IllegalStateException("삭제된 게시글입니다.");
        }
        return goods;
    }

    public WantedGoods findWantedGoods(Long wantedId) {
        WantedGoods wantedGoods = wantedGoodsRepository.findById(wantedId).orElseThrow(() ->
                new NullPointerException("해당 게시글은 존재하지 않습니다."));
        return wantedGoods;
    }


    private Pageable paging(int page, int size, String sortBy, boolean isAsc) {
        // 정렬
        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // pageable 생성
        return PageRequest.of(page, size, sort);
    }


    public List<GoodsRecentDto> recentGoods(HttpServletResponse response) {
        List<GoodsRecentDto> goodsRecentDtos = new ArrayList<>();
        // 조회하면 리스트에 id, productname add 해주기

        Cookie goodsCookie = new Cookie(RECENT_GOODS, UriUtils.encode(String.join(",", goodsRecent), "UTF-8")); // 문자열만 저장 가능
        goodsCookie.setMaxAge(24 * 60 * 60); // 하루동안 저장
        response.addCookie(goodsCookie); // 전송

        for (String id : goodsRecent) {
            Goods goods = goodsRepository.findById(Long.parseLong(id)).orElse(null);
            GoodsRecentDto goodsRecentDto = new GoodsRecentDto(goods);
            goodsRecentDtos.add(goodsRecentDto);
        }
        return goodsRecentDtos;
    }

    public List<GoodsResponseDto> getMyGoodsWithoutPagenation(User user) {
        return getGoodsResponseDtos(user);
    }

    public ApiResponse<UrPocketResponseDto> getPocket(String nickname, UserDetailsImpl userDetails, int page, int size, String sortBy, boolean isAsc) {
        User user = userRepository.findUserByNickname(nickname);
        if (userDetails != null) { // 로그인 된 경우다 !!
            if (user.getUserId() == userDetails.getUser().getUserId()) {
                throw new IllegalArgumentException("같은 사용자입니다 ");
            }
        }
        if (userDetails != null) { // 로그인 된 경우다 !!
            if (user.getUserId() == userDetails.getUser().getUserId()) {
                throw new IllegalArgumentException("같은 사용자입니다 ");
            }
        }
        Pageable pageable = paging(page, size, sortBy, isAsc);
        Page<Goods> goodsList = goodsRepository.findAllByUserAndIsDeletedFalse(user, pageable);
        List<GoodsListResponseDto> myGoods = new ArrayList<>();
        for (Goods goods : goodsList) {
            Image firstImage = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());

            boolean checkDibs = false;
            if (userDetails != null) {
                checkDibs = dibsService.checkDibsGoods(userDetails.getUser().getUserId(), goods.getGoodsId());
            }

            myGoods.add(new GoodsListResponseDto(goods, firstImage.getImageUrl(), checkDibs));
        }

        UrPocketResponseDto urPocketResponseDto = new UrPocketResponseDto(user, myGoods);

        return new ApiResponse<>(true, urPocketResponseDto, null);
//        return getGoodsResponseDtos(user);
    }

    private List<GoodsResponseDto> getGoodsResponseDtos(User user) {
        List<Goods> goodsList = goodsRepository.findAllByUserAndIsDeletedFalseAndGoodsStatus(user, GoodsStatus.ONSALE);
        List<GoodsResponseDto> goodsResponseDtoList = new ArrayList<>();
        for (Goods goods : goodsList) {
            List<String> imageUrls = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAsc(goods.getGoodsId())
                    .stream()
                    .map(Image::getImageUrl)
                    .collect(Collectors.toList());
            goodsResponseDtoList.add(new GoodsResponseDto(goods, imageUrls));
        }
        return goodsResponseDtoList;
    }

    public ApiResponse<List<GoodsListResponseDto>> searchGoods(String keyword) {
        List<Goods> searchList = goodsRepository.findByTitleContaining(keyword);
        List<GoodsListResponseDto> goodsListResponseDtos = new ArrayList<>();
        for (Goods search : searchList) {
            goodsListResponseDtos.add(new GoodsListResponseDto(search));
        }
        return new ApiResponse<>(true, goodsListResponseDtos, null);
    }


    // 로그인 없이 조회
    public ApiResponse<GoodsResponseDto> getGoodsEveryone(Long goodsId) {
        Goods goods = findGoods(goodsId);
        WantedGoods wantedGoods = findWantedGoods(goodsId);
        List<Image> images = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAsc(goodsId);
        List<String> imageUrls = images.stream()
                .map(Image::getImageUrl)
                .collect(Collectors.toList());
        if (goodsRecent.size() >= MAX_RECENT_GOODS) {
            goodsRecent.remove(0);
        }
        goodsRecent.add(Long.toString(goods.getGoodsId())); // 조회시에 리스트에 추가 !
        return new ApiResponse<>(true, new GoodsResponseDto(goods, imageUrls, wantedGoods), null);
    }

    //교환 요청 받은 페이지
//    public ResponseEntity<Page<GoodsResponseListDto>> requestedTradeList(User user, int page, int size, String sortBy, boolean isAsc,
//                                                                         RequestedStatus requestedStatus) {
////        Pageable pageable = paging(page, size, sortBy, isAsc);
////        Page<Goods> myGoodsPage;
//
////        if (goodsStatus != null) {
////            myGoodsPage = goodsRepository.findByUserUserIdAndGoodsStatus(user.getUserId(), pageable, goodsStatus);
////        } else {
////            myGoodsPage = goodsRepository.findByUserUserId(user.getUserId(), pageable);
////        }
//
////        List<GoodsListResponseDto> goodsListResponseDtoList = myGoodsPage.stream()
////                .map(goods -> {
////                    Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
////                    return new GoodsListResponseDto(goods, image.getImageUrl());
////                })
////                .collect(Collectors.toList());
//
//        Pageable pageable = paging(page, size, sortBy, isAsc);
//        Page<Goods> myGoodsPage;
//
//        if (requestedStatus != null) {
//            myGoodsPage = goodsRepository.findByUserUserIdAndRequestedStatus(user.getUserId(), pageable, requestedStatus);
//        } else {
//            myGoodsPage = goodsRepository.findByUserUserId(user.getUserId(), pageable);
//        }
//
//        for (Goods goods : myGoodsPage) {
//            if (goods.getRequestedStatus().equals(RequestedStatus.REQUESTED)) {
//                List<GoodsResponseListDto> goodsListResponseDtos = requestRepository.findByGoodsGoodsIdAndRequestStatus(goods.getGoodsId(), RequestStatus.REQUEST)
//                        .stream()
//                        .map(requestGoods -> {
//                            Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
//                            return new GoodsResponseListDto(goods, image.getImageUrl(), requestGoods);
//                        })
//                        .collect(Collectors.toList());
//            } else if (goods.getRequestedStatus().equals(RequestedStatus.TRADING)) {
//                List<GoodsResponseListDto> goodsListResponseDtos = requestRepository.findByGoodsGoodsIdAndRequestStatus(goods.getGoodsId(), RequestStatus.TRADING)
//                        .stream()
//                        .map(requestGoods -> {
//                            Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
//                            return new GoodsResponseListDto(goods, image.getImageUrl(), requestGoods);
//                        })
//                        .collect(Collectors.toList());
//            } else if (goods.getRequestedStatus().equals(RequestedStatus.DONE)) {
//                List<GoodsResponseListDto> goodsListResponseDtos = requestRepository.findByGoodsGoodsIdAndRequestStatus(goods.getGoodsId(), RequestStatus.DONE)
//                        .stream()
//                        .map(requestGoods -> {
//                            Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
//                            return new GoodsResponseListDto(goods, image.getImageUrl(), requestGoods);
//                        })
//                        .collect(Collectors.toList());
//            } else if (goods.getRequestedStatus().equals(RequestedStatus.CANCEL)) {
//                List<GoodsResponseListDto> goodsListResponseDtos = requestRepository.findByGoodsGoodsIdAndRequestStatus(goods.getGoodsId(), RequestStatus.CANCEL)
//                        .stream()
//                        .map(requestGoods -> {
//                            Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
//                            return new GoodsResponseListDto(goods, image.getImageUrl(), requestGoods);
//                        })
//                        .collect(Collectors.toList());
//            }
//            PageResponse response = new PageResponse<>(goodsListResponseDtos, pageable, myGoodsPage.getTotalElements());
//        }
////
////            }{
////                Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
////                GoodsListResponseDto goodsListResponseDto = new GoodsListResponseDto(goods, image.getImageUrl());
////                goodsListResponseDtos.add(goodsListResponseDto);
////
////                List<RequestGoods> requestGoodsList = requestRepository.findByGoodsGoodsIdAndRequestStatus(goods.getGoodsId(), goods.getGoodsStatus());
////                for (RequestGoods requestGoods : requestGoodsList) {
////                    Image image1 = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
////                    GoodsListResponseDto goodsListResponseDto = new GoodsListResponseDto(goods, image1.getImageUrl(), requestGoods);
////                    goodsListResponseDtos.add(goodsListResponseDto);
////                }
////            }
//
//
//        PageResponse response = new PageResponse<>(goodsListResponseDtos, pageable, myGoodsPage.getTotalElements());
//        return ResponseEntity.status(HttpStatus.OK.value()).body(response);
//    }
//
//    public ResponseEntity<Page<GoodsListResponseDto>> requestTradeList(User user, int page, int size, String sortBy, boolean isAsc,
//                                                                       RequestStatus requestStatus) {
//
//        Pageable pageable = paging(page, size, sortBy, isAsc);
//        Page<RequestGoods> myGoodsPage;
//
//        if (requestStatus != null) {
//            myGoodsPage = requestRepository.findByUserUserIdAndRequestStatus(user.getUserId(), pageable, requestStatus);
//        } else {
//            myGoodsPage = requestRepository.findByUserUserId(user.getUserId(), pageable);
//        }
//
//        List<GoodsListResponseDto> goodsListResponseDtoList = myGoodsPage.stream()
//                .map(goods -> {
//                    Image image = imageRepository.findByGoodsGoodsIdOrderByCreatedAtAscFirst(goods.getGoodsId());
//                    return new GoodsListResponseDto(goods, image.getImageUrl());
//                })
//                .collect(Collectors.toList());
//
//        PageResponse response = new PageResponse<>(goodsListResponseDtoList, pageable, myGoodsPage.getTotalElements());
//        return ResponseEntity.status(HttpStatus.OK.value()).body(response);
//    }

    //알림과 카운트 올려줘야됨
    public ResponseDto goodsRequest(User user, GoodsRequestRequestDto goodsRequestRequestDto, Long urGoodsId) {
        Goods urGoods = goodsRepository.findByGoodsId(urGoodsId)
                .orElseThrow(() -> new NullPointerException("해당 상품이 존재하지 않습니다."));

        for (Long goodsId : goodsRequestRequestDto.getGoodsId()) {
            Goods goods = goodsRepository.findById(goodsId).orElseThrow(
                    () -> new IllegalArgumentException("존재하지 않는 goodsId 입니다."));

            if (!goods.getUser().getUserId().equals(user.getUserId())) {
                if (goods.getGoodsStatus().equals(GoodsStatus.ONSALE) ||
                        goods.getGoodsStatus().equals(GoodsStatus.REQUESTED)) {
                    new RequestGoods(user, goods, RequestStatus.REQUEST);
                } else {
                    throw new IllegalArgumentException("해당 물품은 다른 곳에 사용되거나 판매중 상태가 아닙니다.");
                }
            } else {
                throw new IllegalArgumentException("내 물건은 교환할 수 없습니다.");
            }
        }
        urGoods.setRequestedStatus(RequestedStatus.REQUESTED);

        return new ResponseDto("교환신청이 완료되었습니다.", HttpStatus.OK.value(), "OK");
    }
}
