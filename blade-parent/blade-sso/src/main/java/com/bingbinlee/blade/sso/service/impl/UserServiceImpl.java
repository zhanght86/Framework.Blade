package com.bingbinlee.blade.sso.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.bingbinlee.blade.common.utils.CookieUtils;
import com.bingbinlee.blade.common.utils.JsonUtils;
import com.bingbinlee.blade.mapper.UserMapper;


import com.bingbinlee.blade.sso.dao.JedisClient;
import com.bingbinlee.blade.common.pojo.BladeResult;
import com.bingbinlee.blade.common.utils.ExceptionUtil;
import com.bingbinlee.blade.pojo.User;
import com.bingbinlee.blade.pojo.UserExample;
import com.bingbinlee.blade.pojo.UserExample.Criteria;
import com.bingbinlee.blade.sso.service.UserService;

/**
 * @Description 用户管理Service
 * @author	libingbin2015@aliyun.com
 * @date	2016年11月14日上午10:28:39
 * @version 1.0
 */
@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private UserMapper userMapper;
	
	@Autowired
	private JedisClient jedisClient;
	
	@Value("${REDIS_USER_SESSION_KEY}")
	private String REDIS_USER_SESSION_KEY;
	@Value("${SSO_SESSION_EXPIRE}")
	private Integer SSO_SESSION_EXPIRE;
	
	@Override
	public BladeResult checkData(String content, Integer type) {
		//创建查询条件
		UserExample example = new UserExample();
		Criteria criteria = example.createCriteria();
		//对数据进行校验：1、2、3分别代表username、phone、email
		//用户名校验
		if (1 == type) {
			criteria.andUsernameEqualTo(content);
		//电话校验
		} else if ( 2 == type) {
			criteria.andPhoneEqualTo(content);
		//email校验
		} else {
			criteria.andEmailEqualTo(content);
		}
		//执行查询
		List<User> list = userMapper.selectByExample(example);
		if (list == null || list.size() == 0) {
			return BladeResult.ok(true);
		}
		return BladeResult.ok(false);
	}

	@Override
	public BladeResult createUser(User user) {
		user.setUpdated(new Date());
		user.setCreated(new Date());
		//md5加密
		user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
		userMapper.insert(user);
		return BladeResult.ok();
	}

	/**
	 * 用户登录
	 * <p>Title: userLogin</p>
	 * <p>Description: </p>
	 * @param username
	 * @param password
	 * @return
	 * @see com.bingbinlee.blade.sso.service.UserService#userLogin(String username, String password, HttpServletRequest request, HttpServletResponse response)
	 */
	@Override
	public BladeResult userLogin(String username, String password,
			HttpServletRequest request, HttpServletResponse response) {
		
		UserExample example = new UserExample();
		Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(username);
		List<User> list = userMapper.selectByExample(example);
		//如果没有此用户名
		if (null == list || list.size() == 0) {
			return BladeResult.build(400, "用户名或密码错误");
		}
		User user = list.get(0);
		//比对密码
		if (!DigestUtils.md5DigestAsHex(password.getBytes()).equals(user.getPassword())) {
			return BladeResult.build(400, "用户名或密码错误");
		}
		//生成token
		String token = UUID.randomUUID().toString();
		//保存用户之前，把用户对象中的密码清空。
		user.setPassword(null);
		//把用户信息写入redis
		jedisClient.set(REDIS_USER_SESSION_KEY + ":" + token, JsonUtils.objectToJson(user));
		//设置session的过期时间
		jedisClient.expire(REDIS_USER_SESSION_KEY + ":" + token, SSO_SESSION_EXPIRE);
		
		//添加写cookie的逻辑，cookie的有效期是关闭浏览器就失效。
		CookieUtils.setCookie(request, response, "TT_TOKEN", token);
		
		//返回token
		return BladeResult.ok(token);
	}

	@Override
	public BladeResult getUserByToken(String token) {
		
		//根据token从redis中查询用户信息
		String json = jedisClient.get(REDIS_USER_SESSION_KEY + ":" + token);
		//判断是否为空
		if (StringUtils.isBlank(json)) {
			return BladeResult.build(400, "此session已经过期，请重新登录");
		}
		//更新过期时间
		jedisClient.expire(REDIS_USER_SESSION_KEY + ":" + token, SSO_SESSION_EXPIRE);
		//返回用户信息
		return BladeResult.ok(JsonUtils.jsonToPojo(json, User.class));
	}

}