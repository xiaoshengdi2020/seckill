package com.github.lyrric.service;

import com.alibaba.fastjson.JSONObject;
import com.github.lyrric.conf.Config;
import com.github.lyrric.model.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created on 2020-07-22.
 *
 * @author wangxiaodong
 */
public class HttpService {

    private String baseUrl = "https://miaomiao.scmttec.com";

    private final Logger logger = LogManager.getLogger(HttpService.class);


    /***
     * 获取秒杀资格
     * @param seckillId 疫苗ID
     * @param vaccineIndex 固定1
     * @param linkmanId 接种人ID
     * @param idCard 接种人身份证号码
     * @return 返回订单ID
     * @throws IOException
     * @throws BusinessException
     */
    public String secKill(String seckillId, String vaccineIndex, String linkmanId, String idCard, String st) throws IOException, BusinessException {
        String path = baseUrl+"/seckill/seckill/subscribe.do";
        Map<String, String> params = new HashMap<>();
        params.put("seckillId", seckillId);
        params.put("vaccineIndex", vaccineIndex);
        params.put("linkmanId", linkmanId);
        params.put("idCardNo", idCard);
        //加密参数
        Header header = new BasicHeader("ecc-hs", eccHs(seckillId, st));
        return get(path, params, header);
    }

    /**
     * 获取疫苗列表
     * @return
     * @throws BusinessException
     */
    public List<VaccineList> getVaccineList() throws BusinessException, IOException {
        hasAvailableConfig();
        String path = baseUrl+"/seckill/seckill/list.do";
        Map<String, String> param = new HashMap<>();
        //九价疫苗的code
        param.put("offset", "0");
        param.put("limit", "100");
        //这个应该是成都的行政区划前四位
        param.put("regionCode", Config.regionCode);
        String json = get(path, param, null);
        return JSONObject.parseArray(json).toJavaList(VaccineList.class);
    }


    /**
     * 获取接种人信息
     * @return
     */
    public List<Member> getMembers() throws IOException, BusinessException {
        String path = baseUrl + "/seckill/linkman/findByUserId.do";
        String json = get(path, null, null);
        logger.info(json);
        return  JSONObject.parseArray(json, Member.class);
    }
    /***
     * 获取加密参数st
     * @param vaccineId 疫苗ID
     */
    public String getSt(String vaccineId) throws IOException {
        String path = baseUrl+"/seckill/seckill/checkstock2.do";
        Map<String, String> params = new HashMap<>();
        params.put("id", vaccineId);
        String json =  get(path, params, null);
        JSONObject jsonObject = JSONObject.parseObject(json);
        return jsonObject.getString("st");
    }

    /***
     * log接口，不知道有何作用，但返回值会设置一个名为tgw_l7_route的cookie
     * @param vaccineId 疫苗ID
     */
    public void log(String vaccineId) throws IOException {
        String path = baseUrl+"/seckill/seckill/log.do";
        Map<String, String> params = new HashMap<>();
        params.put("id", vaccineId);
        get(path, params, null);
    }

    private void hasAvailableConfig() throws BusinessException {
        if(Config.cookie.isEmpty()){
            throw new BusinessException("0", "请先配置cookie");
        }
    }

    private String get(String path, Map<String, String> params, Header extHeader) throws IOException, BusinessException {
        if(params != null && params.size() !=0){
            StringBuilder paramStr = new StringBuilder("?");
            params.forEach((key,value)->{
                paramStr.append(key).append("=").append(value).append("&");
            });
            String t = paramStr.toString();
            if(t.endsWith("&")){
                t = t.substring(0, t.length()-1);
            }
            path+=t;
        }
        HttpGet get = new HttpGet(path);
        List<Header> headers = getCommonHeader();
        if(extHeader != null){
            headers.add(extHeader);
        }
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(2500)
                .setSocketTimeout(2500)
                .setConnectTimeout(2500)
                .build();
        get.setConfig(requestConfig);
        get.setHeaders(headers.toArray(new Header[0]));
        CloseableHttpClient httpClient = HttpClients.createDefault();
        System.out.println(get.getHeaders("Cookie"));
        Header[] allHeaders = get.getAllHeaders();

        for (Header allHeader : allHeaders) {
            System.out.println(allHeader.getValue());

        }
        CloseableHttpResponse response = httpClient.execute(get);
        dealHeader(response);
        HttpEntity httpEntity = response.getEntity();
        String json =  EntityUtils.toString(httpEntity, StandardCharsets.UTF_8);
        JSONObject jsonObject = JSONObject.parseObject(json);
        System.out.println(json);
        if("0000".equals(jsonObject.get("code"))){
            return jsonObject.getString("data");
        }else{
            throw new BusinessException(jsonObject.getString("code"), jsonObject.getString("msg"));
        }
    }

    private void dealHeader(CloseableHttpResponse response){
        Header[] responseHeaders = response.getHeaders("Set-Cookie");
        if (responseHeaders.length > 0) {
            for (Header responseHeader : responseHeaders) {
                String cookie = ((BufferedHeader) responseHeader).getBuffer().toString().split(";")[0].split(":")[1].trim();
                String[] split = cookie.split("=");
                Config.cookie.put(split[0], cookie);
            }
        }
    }

    private List<Header> getCommonHeader(){
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("User-Agent", "Mozilla/5.0 (iPad; CPU OS 15_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.29(0x18001d30) NetType/WIFI Language/zh_CN\n"));
        headers.add(new BasicHeader("Referer", "https://servicewechat.com/wxff8cad2e9bf18719/2/page-frame.html"));
        headers.add(new BasicHeader("tk", Config.tk));
        headers.add(new BasicHeader("Accept","application/json, text/plain, */*"));
        headers.add(new BasicHeader("Host","miaomiao.scmttec.com"));
        //headers.add(new BasicHeader("Cookie","tgw_l7_route=310b1314d3b7b84666fb433380f2a0d4; _xxhm_=%7B%22id%22%3A14428383%2C%22mobile%22%3A%2215997353275%22%2C%22nickName%22%3A%22%E6%B5%81%E8%BF%9E%22%2C%22headerImg%22%3A%22https%3A%2F%2Fthirdwx.qlogo.cn%2Fmmopen%2Fvi_32%2F9GqfVRV24biaDudggxd87NtMbNXqU1jcVodNN6KUbR1xDaBn3U7ygzosXbbicEZ6O541e5UYCT1dV5ZLU1hAYNwA%2F132%22%2C%22regionCode%22%3A%22420111%22%2C%22name%22%3A%22%E5%8F%B8*%E8%83%9C%22%2C%22uFrom%22%3A%22syswhxg210319%22%2C%22wxSubscribed%22%3A1%2C%22birthday%22%3A%222000-07-17+02%3A00%3A00%22%2C%22sex%22%3A1%2C%22hasPassword%22%3Atrue%2C%22birthdayStr%22%3A%222000-07-17%22%7D; _xzkj_=wxapptoken:10:4481538e5d4f545750a4d5ac19bd2c87_76f5bab51f8e68e3686960f4fde73663; c7f4=e0398f5e8c3254df2f; 6189=ef77cffd51ea0a9fec; 7b6e=c68ac50c6f3d17c8ea; 1bc4=087f2d04432642e83a; 2c0b=37eff5b6c68d693dc6\n"));

//        if(!Config.cookie.isEmpty()){
//            String cookie = String.join("; ", new ArrayList<>(Config.cookie.values()));
//            logger.info("cookie is {}", cookie);
//            headers.add(new BasicHeader("Cookie", cookie));
//        }
        return headers;
    }

    private String eccHs(String seckillId, String st){
        String salt = "ux$ad70*b";
        final Integer memberId = Config.memberId;
        String md5 = DigestUtils.md5Hex(seckillId + memberId + st);
        return DigestUtils.md5Hex(md5 + salt);
    }

    public static void main(String[] args) {
        String salt = "ux$ad70*b";
        Integer memberId = 12372032;
        String md5 = DigestUtils.md5Hex("1085" + memberId + "1630902134216");
        System.out.println(DigestUtils.md5Hex(md5 + salt));
    }
}
