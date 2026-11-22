package com.yangstagram.shop;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.shop.ProductService.CreateProduct;
import com.yangstagram.shop.ProductService.OrderView;
import com.yangstagram.shop.ProductService.ProductDetail;
import com.yangstagram.shop.ProductService.ProductView;
import com.yangstagram.shop.ProductService.TagInput;
import com.yangstagram.shop.SubscriptionService.ExclusiveItem;
import com.yangstagram.shop.SubscriptionService.GiftResult;
import com.yangstagram.shop.SubscriptionService.SubscriptionInfo;
import com.yangstagram.user.dto.UserSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 쇼핑(제품·태그·위시리스트·가짜 주문)과 구독·선물 API */
@RestController
@RequestMapping("/api")
public class ShopController {

    private final ProductService products;
    private final SubscriptionService subscriptions;

    public ShopController(ProductService products, SubscriptionService subscriptions) {
        this.products = products;
        this.subscriptions = subscriptions;
    }

    public record OrderRequest(Integer quantity) {
    }

    public record TagRequest(List<TagInput> products) {
    }

    public record PriceRequest(Integer price) {
    }

    public record GiftRequest(int stars) {
    }

    // ------------------------------------------------------------ 제품

    @GetMapping("/users/{username}/products")
    public List<ProductView> shop(@PathVariable String username) {
        return products.shop(username, CurrentUser.requireId());
    }

    @GetMapping("/products")
    public List<ProductView> search(@RequestParam(required = false) String q) {
        return products.search(q, CurrentUser.requireId());
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@RequestPart("image") MultipartFile image, @RequestPart("data") CreateProduct data) {
        return products.create(CurrentUser.requireId(), image, data);
    }

    @GetMapping("/products/{id}")
    public ProductDetail detail(@PathVariable long id) {
        return products.detail(id, CurrentUser.requireId());
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        products.delete(id, CurrentUser.requireId());
    }

    @PutMapping("/products/{id}/wishlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void wish(@PathVariable long id) {
        products.wish(id, CurrentUser.requireId(), true);
    }

    @DeleteMapping("/products/{id}/wishlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwish(@PathVariable long id) {
        products.wish(id, CurrentUser.requireId(), false);
    }

    @GetMapping("/me/wishlist")
    public List<ProductView> wishlist() {
        return products.wishlist(CurrentUser.requireId());
    }

    /** 가짜 결제 */
    @PostMapping("/products/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView order(@PathVariable long id, @RequestBody OrderRequest body) {
        return products.order(id, CurrentUser.requireId(), body.quantity() == null ? 1 : body.quantity());
    }

    @GetMapping("/me/orders")
    public List<OrderView> orders() {
        return products.orders(CurrentUser.requireId());
    }

    @PutMapping("/posts/{shortcode}/products")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void tag(@PathVariable String shortcode, @RequestBody TagRequest body) {
        products.tagPost(shortcode, CurrentUser.requireId(), body.products());
    }

    // ------------------------------------------------------------ 구독·선물

    @GetMapping("/users/{username}/subscription")
    public SubscriptionInfo subscription(@PathVariable String username) {
        return subscriptions.info(username, CurrentUser.requireId());
    }

    @PostMapping("/users/{username}/subscription")
    public SubscriptionInfo subscribe(@PathVariable String username) {
        return subscriptions.subscribe(username, CurrentUser.requireId());
    }

    @DeleteMapping("/users/{username}/subscription")
    public SubscriptionInfo cancel(@PathVariable String username) {
        return subscriptions.cancel(username, CurrentUser.requireId());
    }

    @GetMapping("/users/{username}/exclusive")
    public List<ExclusiveItem> exclusive(@PathVariable String username) {
        return subscriptions.exclusive(username, CurrentUser.requireId());
    }

    @GetMapping("/me/subscriptions")
    public List<SubscriptionInfo> mySubscriptions() {
        return subscriptions.mine(CurrentUser.requireId());
    }

    @GetMapping("/me/subscribers")
    public List<UserSummary> mySubscribers() {
        return subscriptions.subscribers(CurrentUser.requireId());
    }

    /** 내 구독 가격 (null이면 구독 받지 않음) */
    @PutMapping("/me/subscription-price")
    public Map<String, Integer> setPrice(@RequestBody PriceRequest body) {
        Map<String, Integer> result = new HashMap<>();
        result.put("price", subscriptions.setPrice(CurrentUser.requireId(), body.price()));
        return result;
    }

    @PostMapping("/posts/{shortcode}/gifts")
    public GiftResult gift(@PathVariable String shortcode, @RequestBody GiftRequest body) {
        return subscriptions.gift(shortcode, CurrentUser.requireId(), body.stars());
    }
}
