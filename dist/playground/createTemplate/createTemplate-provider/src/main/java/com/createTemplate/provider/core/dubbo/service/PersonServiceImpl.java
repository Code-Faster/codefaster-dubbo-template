package com.createTemplate.provider.core.dubbo.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.transaction.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.createTemplate.api.core.doubbo.service.TAccountDetailService;
import com.createTemplate.api.core.doubbo.service.WxService;
import com.createTemplate.api.util.*;
import com.createTemplate.model.constant.CommonConstant;
import com.createTemplate.model.core.pojo.TAccountDetail;
import com.createTemplate.model.core.pojo.TAdvanceOrder;
import com.createTemplate.model.core.pojo.TRechargeOrder;
import com.createTemplate.provider.config.IdWorker;
import com.createTemplate.provider.config.WxPublicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.core.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.createTemplate.api.base.dubbo.service.CommonRedisDao;
import com.createTemplate.api.common.redission.DistributedQueue;
import com.createTemplate.api.core.doubbo.service.PersonService;
import com.createTemplate.model.constant.RedisConstant;
import com.createTemplate.model.core.pojo.TPerson;
import com.createTemplate.model.core.vo.TPersonVO;
import com.createTemplate.model.exception.BusinessException;
import com.createTemplate.model.mybatis.page.PageParameter;
import com.createTemplate.model.resultvo.Grid;
import com.createTemplate.provider.base.dao.impl.MybatisDaoImpl;
import com.createTemplate.provider.config.RedisConfig;
import org.springframework.beans.factory.annotation.Autowired;

@Service(version = "1.0.0", retries = -1, timeout = 6000)
@Slf4j
public class PersonServiceImpl extends MybatisDaoImpl implements PersonService {

    public final String className = "person";

    @Autowired
    private IdWorker idWorker;

    @Autowired
    RedisConfig redisConfig;
    @Autowired
    private WxPublicConfig wxPublicConfig;

    /**
     * ??????ID??????????????????
     *
     * @author libiqi
     * @date 2018???5???21???
     */
    @Override
    public void updatePersonById(TPersonVO obj) {
        TPerson oldObj = findById(obj.getId());
        if (oldObj == null) {
            throw new BusinessException("???????????????");
        }
        DataUtils.copyPropertiesIgnoreNull(obj, oldObj);
        super.update(oldObj);
        TPersonVO dbPersonVo = findPersonDetailById(obj.getId());
        updateSessionByUid(dbPersonVo);
    }

    /**
     * ??????ID ??????person
     *
     * @author libiqi
     * @date 2018???5???14???
     */
    @Override
    public TPerson findById(Long id) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", id);
        map.put("column", PropertyUtils.getPropertyNames(TPerson.class));
        return this.baseDao.selectOne(className + ".findById", map);
    }

    /**
     * ????????????????????????
     *
     * @author libiqi
     * @date 2018???5???14???
     */
    @Override
    public Grid findPersonPage(TPersonVO vo) {
        vo.setPageParameter(new PageParameter(vo.getPage(), vo.getRows()));
        vo.setColumn(PropertyUtils.getPropertyNames(TPerson.class, "p"));
        List<TPerson> list = this.baseDao.selectList(className + ".findPersonPage", vo);
        Grid grid = new Grid();
        grid.setCount(Long.valueOf(vo.getPageParameter().getTotalCount()));
        grid.setList(list);
        return grid;
    }

    /**
     * ??????token ??????redis ??????id ?????? ????????????token??????????????????
     *
     * @author libiqi
     * @date 2018???4???12???
     */
    @Override
    public Map<String, Object> setPersonToRedis(TPersonVO vo) {
        CommonRedisDao commonRedisDao = applicationContext.getBean(CommonRedisDao.class);
        if (null == vo) {
            throw new BusinessException("??????????????????!");
        }
//        this.clearPersonRedis(vo.getId());
        String seed = StringUtil.md5(vo.getId().toString());
        String token;
        Integer expireTime = 60 * 2;
        if (StringUtils.isBlank(vo.getAuthToken())) {
            token = commonRedisDao.get(RedisConstant.personSeed + seed);
            if (StringUtils.isBlank(token)) {
                token = TokenProcessor.getInstance().generateToken(seed);
            }
        } else {
            token = vo.getAuthToken();
        }
        commonRedisDao.set(RedisConstant.personSeed + seed, token);
        // ?????????????????? 2??????
        commonRedisDao.set(RedisConstant.personToken + token, JSON.toJSONString(vo), expireTime);
        Map<String, Object> map = new HashMap<String, Object>();
        // ??????token
        map.put("token", token);
        // ??????????????????
        map.put("expireTime", expireTime);
        return map;
    }

    /**
     * ??????redis
     *
     * @param id
     * @author libiqi
     * @date 2018???5???15???
     */
    public void clearPersonRedis(Long id) {
        CommonRedisDao commonRedisDao = applicationContext.getBean(CommonRedisDao.class);
        if (null != id) {
            String seed = StringUtil.md5(id.toString());
            String token = commonRedisDao.get(RedisConstant.personSeed + seed);
            if (null != token) {
                commonRedisDao.delete(RedisConstant.personToken + token);
            }
        } else {
            throw new BusinessException("id????????????!");
        }
    }

    /**
     * ??????token??????????????????session
     *
     * @author libiqi
     * @date 2018???5???15???
     */
    @Override
    public TPersonVO getCurrentUser(String authToken) {
        CommonRedisDao commonRedisDao = applicationContext.getBean(CommonRedisDao.class);
        String redisPerson = commonRedisDao.get(RedisConstant.personToken + authToken);
        if (StringUtils.isBlank(redisPerson)) {
            return null;
        }
        TPersonVO vo = JSON.parseObject(redisPerson, TPersonVO.class);
        vo.setAuthToken(authToken);
        return vo;
    }

    /**
     * ????????????uid ????????????session
     *
     * @param findPersonDetailById
     */
    private void updateSessionByUid(TPersonVO findPersonDetailById) {
        CommonRedisDao commonRedisDao = applicationContext.getBean(CommonRedisDao.class);
        // ?????????token
        String seed = StringUtil.md5(findPersonDetailById.getId().toString());
        String token = commonRedisDao.get(RedisConstant.personSeed + seed);
        if (StringUtils.isNotEmpty(token)) {
            findPersonDetailById.setAuthToken(token);
            this.setPersonToRedis(findPersonDetailById);
        }
    }

    /**
     * ????????????ID??????????????????
     *
     * @param userId
     * @return
     */
    @Override
    public TPersonVO findPersonDetailById(Long userId) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("id", userId);
        map.put("column", PropertyUtils.getPropertyNames(TPerson.class));
        return baseDao.selectOne(className + ".findPersonDetailById", map);
    }

    /**
     * @param ids
     * @return
     * @throws BusinessException
     */
    @Override
    public List<TPerson> findByIds(Set<Long> ids) throws BusinessException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("ids", ids);
        map.put("column", PropertyUtils.getPropertyNames(TPerson.class));
        return this.baseDao.selectList(className + ".findByIds", map);
    }

    /**
     * ???????????????ID ?????? ??????????????????
     *
     * @author libiqi
     * @date 2018???5???23???
     */
    @Override
    public TPersonVO saveOrloginByAppletOpenId(String softOpenId, JSONObject userInfo) {
        String key = "saveOrloginByAppletOpenId_" + softOpenId;
        // ??????(????????????)
        Redisson redisson = DistributedQueue.getRedisson(redisConfig.getAddress(), redisConfig.getPassword());
        // ???activityId????????????
        RLock rlock = redisson.getLock(key);
        // ???????????? ????????? + ?????????????????????
        rlock.lock(5, TimeUnit.SECONDS);
        try {
            TPersonVO personVo = findPersonByAppId(softOpenId);
            if (null == personVo) {
                personVo = new TPersonVO();
                personVo.setAppId(softOpenId);
                /** ?????????????????? */
                personVo.setId(null);
                personVo.setInputDate(new Date());
                personVo.setStatus(0);
                if (userInfo != null) {
                    personVo.setNickName(userInfo.getString("name"));

                }
                TPerson newPerson = new TPerson();
                // ????????????
                DataUtils.copyPropertiesIgnoreNull(personVo, newPerson);
                /** ?????????????????? */
                Long id = super.save(newPerson);
                // ??????????????????ID
                personVo.setId(id);
            }
            Map<String, Object> setPersonToRedis = setPersonToRedis(personVo);
            personVo.setAuthToken(setPersonToRedis.get("token").toString());
            return personVo;
        } catch (BusinessException e) {
            throw new BusinessException(e.getMessage(), e.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private TPersonVO findPersonByAppId(String softOpenId) {
        TPersonVO person = new TPersonVO();
        person.setColumn(PropertyUtils.getPropertyNames(TPerson.class, "p"));
        person.setAppId(softOpenId);
        List<TPersonVO> list = this.baseDao.selectList(className + ".findPersonByAppId", person);
        if (list == null || list.size() == 0) {
            return null;
        }
        TPersonVO personVo = list.get(0);
        return personVo;
    }

    /**
     * ??????
     *
     * @param tPersonVO
     * @return
     * @throws BusinessException
     */
    @Override
    public String accountsRecharge(TPersonVO tPersonVO) throws BusinessException {
        log.debug("-------------PersonService.accountsRecharge start");
        String resultMap = "";
        WxService wxService = this.applicationContext.getBean(WxService.class);
        TPerson dbPerson = this.findById(tPersonVO.getId());
        if (dbPerson != null && dbPerson.getAppId() != null) {
            TRechargeOrder newTRechargeOrder = new TRechargeOrder();
            newTRechargeOrder.setId(null);
            newTRechargeOrder.setAmount(tPersonVO.getRechargeMoney());
            newTRechargeOrder.setAccountAmount(tPersonVO.getRechargeMoney());
            newTRechargeOrder.setChannel(CommonConstant.channel.CHANNEL_WECHAT);
            newTRechargeOrder.setPersonId(tPersonVO.getId());
            newTRechargeOrder.setStatus(0);
            newTRechargeOrder.setInputDate(new Date());
            newTRechargeOrder.setOrderNo(idWorker.nextId() + "");
            this.save(newTRechargeOrder);
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("type", 0);// ???????????? 0?????? 1 ?????????
            resultMap = wxService.weChatPay(tPersonVO.getRechargeMoney().toString(), String.valueOf(JSON.toJSON(map)),
                    newTRechargeOrder.getOrderNo(), tPersonVO.getSpbillCreateIp(), 1, "????????????");
        } else {
            throw new BusinessException("???????????????????????????openId");
        }
        log.debug("-------------PersonService.accountsRecharge end");
        return resultMap;
    }

    @Override
    public void rechargeOrderCall(String out_trade_no, String transaction_id) throws BusinessException {
        TRechargeOrder dbTRechargeOrder = this.findTRechargeOrder(null, out_trade_no, null, null, null);
        if (dbTRechargeOrder != null) {
            dbTRechargeOrder.setFeeDate(new Date());
            dbTRechargeOrder.setOtherOrderNo(transaction_id);
            dbTRechargeOrder.setStatus(1);
            this.update(dbTRechargeOrder);
            // ????????????????????????
            TAccountDetail newTAccountDetailOne = new TAccountDetail();
            newTAccountDetailOne.setPersonId(dbTRechargeOrder.getPersonId());
            newTAccountDetailOne.setAccount(dbTRechargeOrder.getAmount());
            newTAccountDetailOne.setOrderNo(out_trade_no);
            newTAccountDetailOne.setStatus(TAccountDetail.STATUS_NORMAL);
            newTAccountDetailOne.setType(TAccountDetail.TYPE_RECHARGE);
            newTAccountDetailOne.setRemark("??????");
            TAccountDetailService accountDetailService = this.applicationContext.getBean(TAccountDetailService.class);
            accountDetailService.saveTAccountDetail(newTAccountDetailOne);
        }

    }

    /**
     * ??????????????????
     *
     * @param person_id
     * @return
     */
    @Override
    public int queryPersonSurplusMoeny(Long person_id) {
        log.debug("-------------PersonService.queryPersonSurplusMoeny start");
        return super.baseDao.selectOne(className + ".queryPersonSurplusMoeny", person_id);
    }

    /**
     * ????????????????????????
     *
     * @param vo
     * @return
     */
    @Override
    public Grid<TPersonVO> findRecommendPage(TPersonVO vo) {
        log.debug("-------------PersonService.findRecommendPage start");
        vo.setColumn(PropertyUtils.getPropertyNames(TPerson.class, "p", "password", "open_id"));
        vo.setPageParameter(new PageParameter(vo.getPage(), vo.getRows()));
        Grid<TPersonVO> grid = new Grid<>();
        List<TPersonVO> list = this.baseDao.selectList(className + ".findRecommendPage", vo);
        grid.setCount(Long.valueOf(vo.getPageParameter().getTotalCount()));
        grid.setList(list);
        log.debug("-------------PersonService.findRecommendPage end");
        return grid;
    }

    @Override
    public TRechargeOrder findTRechargeOrder(Long id, String order_no, Long person_id, String status,
                                             Integer recharge_type) {
        log.debug("-------------PersonService.findTRechargeOrder start");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("personId", person_id);
        map.put("id", id);
        map.put("orderNo", order_no);
        map.put("status", status);
        map.put("rechargeType", recharge_type);
        map.put("column", PropertyUtils.getPropertyNames(TRechargeOrder.class));
        log.debug("-------------PersonService.findTRechargeOrder end");
        return super.baseDao.selectOne(className + ".findTRechargeOrder", map);
    }

    /**
     * ??????
     */
    @Override
    public void withdrawDeposit(TPersonVO tPersonVO) throws BusinessException {
        WxService wxService = this.applicationContext.getBean(WxService.class);
        System.out.println(
                "------------------------tPersonVO.getId()---------------------------------" + tPersonVO.getId());
        TPerson dbTPerson = this.findById(tPersonVO.getId());
        if (dbTPerson == null) {
            throw new BusinessException("?????????????????????");
        }
        if (dbTPerson.getAppId() == null) {
            throw new BusinessException("??????????????????????????????");
        }
        if (tPersonVO.getAdvanceMoney().intValue() <= 40) {
            throw new BusinessException("??????????????????0.4???");
        }
        if (tPersonVO.getAdvanceMoney().intValue() > 500000) {
            throw new BusinessException("??????????????????5000???");
        }
        int remainingSum = this.queryPersonFreeSurplusMoeny(tPersonVO.getId());// ??????????????????

        double proportion = 0.006;// ?????????0
        //TODO??? ?????????????????????
        int service_charge = 0;
//		int service_charge = (int) Math.round((tPersonVO.getAdvanceMoney() * proportion));// ??????????????????0.006??????????????????6????????????
        int advance_money = (tPersonVO.getAdvanceMoney() + service_charge);// ????????????+?????????
        if (remainingSum < advance_money) {
            throw new BusinessException("????????????");
        }

        // ?????????????????????
        TAdvanceOrder newTAdvanceOrder = new TAdvanceOrder();
        newTAdvanceOrder.setId(null);
        newTAdvanceOrder.setAmount(tPersonVO.getAdvanceMoney());
        newTAdvanceOrder.setAccountAmount(tPersonVO.getAdvanceMoney());
        newTAdvanceOrder.setChannel(tPersonVO.getAdvanceChannel());
        newTAdvanceOrder.setStatus(0);
        newTAdvanceOrder.setOrderNo(idWorker.nextId() + "");
        newTAdvanceOrder.setPersonId(tPersonVO.getId());
        newTAdvanceOrder.setInputDate(new Date());
        this.save(newTAdvanceOrder);

        // ??????
        int result = wxService.withdrawDeposit(newTAdvanceOrder.getOrderNo(), dbTPerson.getAppId(),
                tPersonVO.getAdvanceMoney(), dbTPerson.getNickName(), tPersonVO.getId(), service_charge);
        if (result == 1) {// result 0???????????? 1????????????
            throw new BusinessException("????????????");
        }
    }

    @Override
    public JSONObject getSessionKeyOropenid(String code) {
        String appId = wxPublicConfig.getAppId();
        String appSecret = wxPublicConfig.getAppSecret();
        log.error("getSessionKeyOropenid appId=" + appId);
        return WXAppletUserInfo.getSessionKeyOropenid(appId, appSecret, code);
    }

    @Override
    public int queryPersonFreeSurplusMoeny(Long personId) {
        log.debug("-------------PersonService.queryPersonFreeSurplusMoeny start");
        return super.baseDao.selectOne(className + ".queryPersonFreeSurplusMoeny", personId);
    }

    @Override
    public int queryPersonFreezonSurplusMoeny(Long person_id) {
        log.debug("-------------PersonService.queryPersonFreezonSurplusMoeny start");
        return super.baseDao.selectOne(className + ".queryPersonFreezonSurplusMoeny", person_id);
    }

    @Override
    public Map getAccountBalanceDetail(Long person_id) throws BusinessException {
        Map result = new HashMap();
        // ????????????
        result.put("personFreeSurplusMoeny", queryPersonFreeSurplusMoeny(person_id));
        // ????????????
        result.put("personFreezonSurplusMoeny", queryPersonFreezonSurplusMoeny(person_id));
        // ?????????
        result.put("personSurplusMoeny", queryPersonSurplusMoeny(person_id));
        return result;
    }
}
