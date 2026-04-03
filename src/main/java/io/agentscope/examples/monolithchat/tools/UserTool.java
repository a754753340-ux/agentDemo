package io.agentscope.examples.monolithchat.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.examples.monolithchat.dto.PackageListRes;
import io.agentscope.examples.monolithchat.dto.ProductRes;
import io.agentscope.examples.monolithchat.dto.UserContext;
import io.agentscope.examples.monolithchat.dto.UserCard;
import io.agentscope.examples.monolithchat.entity.User;
import io.agentscope.examples.monolithchat.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserTool {

    @Autowired
    private UserMapper userMapper;

    @Tool(description = "用户信息")
    public Object getUserInfo(
            UserContext context
    ) {
        User user = userMapper.selectById(context.getUserId());
        String text = "你的昵称是" + user.getNickname() + "，你的性别是" + user.getGender() + "你还有" + user.getDiamond() + "钻石";
        return buildTextEnvelope(text);
    }

    @Tool(description = "推荐异性用户")
    public Object getRecommend(UserContext context) {
        // 目前用随机算法选择异性信息
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(User::getId, context.getUserId()).ne(User::getGender, context.getSex())
                .last("ORDER BY RAND() LIMIT 5");      // 随机5条
        List<User> users = userMapper.selectList(queryWrapper);
        List<UserCard> cards = users.stream().map(this::toUserCard).collect(Collectors.toList());
        return buildImageListEnvelope(cards);
    }

    @Tool(description = "用户查询套餐列表")
    public Object getPackageList(UserContext context) {
        User user = userMapper.selectById(context.getUserId());
        PackageListRes res = new PackageListRes();
        res.setMyDiamonds(user == null ? 0 : user.getDiamond());
        res.setTitle("充值套餐");
        res.setProductsList(buildProducts());
        return buildPackageListEnvelope(res);
    }

    private UserCard toUserCard(User user) {
        UserCard card = new UserCard();
        card.setUserId(user.getId() == null ? null : String.valueOf(user.getId()));
        card.setNickname(user.getNickname());
        card.setAvatar(user.getAvatar());
        card.setGender(user.getGender());
        card.setDiamond(user.getDiamond());
        return card;
    }

    private Map<String, Object> buildTextEnvelope(String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("render_type", "text");
        payload.put("data", data);
        return payload;
    }

    private Map<String, Object> buildImageListEnvelope(List<UserCard> cards) {
        List<Map<String, Object>> images = new ArrayList<>();
        for (UserCard card : cards) {
            if (card == null || card.getAvatar() == null || card.getAvatar().isBlank()) {
                continue;
            }
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("url", card.getAvatar());
            image.put("alt", card.getNickname());
            image.put("userId", card.getUserId());
            images.add(image);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("images", images);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("render_type", "image_list");
        payload.put("data", data);
        return payload;
    }

    private List<ProductRes> buildProducts() {
        List<ProductRes> products = new ArrayList<>();
        products.add(product("prod_1", "pkg_1", "¥", 600, 0, 60, "ios_pkg_1", 1, "", "0.99"));
        products.add(product("prod_2", "pkg_2", "¥", 1200, 10, 120, "ios_pkg_2", 1, "加赠10", "1.99"));
        products.add(product("prod_3", "pkg_3", "¥", 3000, 50, 300, "ios_pkg_3", 2, "首充加赠50", "4.99"));
        products.add(product("prod_4", "pkg_4", "¥", 6800, 120, 680, "ios_pkg_4", 1, "限时加赠120", "9.99"));
        return products;
    }

    private ProductRes product(
            String prodId,
            String packageId,
            String currencySymbol,
            Integer price,
            Integer presentedAmount,
            Integer amount,
            String iosItemId,
            Integer presentType,
            String labelText,
            String usdPrice
    ) {
        ProductRes res = new ProductRes();
        res.setProdId(prodId);
        res.setPackageId(packageId);
        res.setCurrencySymbol(currencySymbol);
        res.setPrice(price);
        res.setPresentedAmount(presentedAmount);
        res.setAmount(amount);
        res.setIosItemId(iosItemId);
        res.setPresentType(presentType);
        res.setLabelText(labelText);
        res.setUsdPrice(usdPrice);
        return res;
    }

    private Map<String, Object> buildPackageListEnvelope(PackageListRes packageListRes) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", buildPackageListText(packageListRes));
        data.put("packageList", packageListRes);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("render_type", "text");
        payload.put("data", data);
        return payload;
    }

    private String buildPackageListText(PackageListRes res) {
        if (res == null || res.getProductsList() == null || res.getProductsList().isEmpty()) {
            return "暂无可用套餐";
        }
        StringBuilder builder = new StringBuilder();
        if (res.getTitle() != null && !res.getTitle().isBlank()) {
            builder.append(res.getTitle()).append('\n');
        }
        builder.append("当前钻石：").append(res.getMyDiamonds() == null ? 0 : res.getMyDiamonds()).append('\n');
        for (ProductRes product : res.getProductsList()) {
            if (product == null) {
                continue;
            }
            builder.append(product.getAmount() == null ? 0 : product.getAmount()).append("钻石");
            if (product.getPresentedAmount() != null && product.getPresentedAmount() > 0) {
                builder.append(" + 赠送").append(product.getPresentedAmount());
            }
            builder.append("，").append(product.getCurrencySymbol() == null ? "" : product.getCurrencySymbol());
            builder.append(product.getPrice() == null ? 0 : product.getPrice() / 100.0).append('\n');
        }
        return builder.toString().trim();
    }
}
