# 一般说明

基础地址（`base-api`）为
* `https://hrznc.haier.net` 或
* `http://120.24.38.2:8080`

对请求的通用要求：
* 所有请求（包括对静态资源的请求）的header里必须有`cs-app-id`字段，登录后必须有`cs-token`字段
* 除密码外，其它所有字符串两端的空白字符都应去掉
* 服务器支持表单与JSON-BODY两种方式提交请求，服务器依据`Content-Type`识别请求的类型。
* 表单方式: `Content-Type:multipart/form-data`
* Json方式: `Content-Type:application/json;charset=UTF-8`
* 关于大小写：客户端上传的密码MD5应为小写；`cs-app-id`值区分大小写。
* 客户端须在请求中声明支持Gzip压缩，参考[Android的gzip处理](http://stackoverflow.com/questions/1573391/android-http-communication-should-use-accept-encoding-gzip)
* 关于日期与时间，分两种表示方式：
* 使用字符串表示时，**采用北京时区（GMT+8）**，日期格式为:`yyyy-MM-dd`, 时间格式为`yyyy-MM-dd HH:mm:ss`
* 也可用长整型unix timestamp表示时间。除非特别说明，timestamp均精确到毫秒


建议，
* 不要传值为空白的参数
* 客户端实现基本参数校验，如字符长度，格式检查
* 客户端应该有处理token过期的机制。(token可能因超时，或同一账号过多设备登录等失效）

## company_id的问题

注册与登录时必须提供`company_id`。且注意区分用于登录的`company_id`与获取称信息的`company_id`

## 标志类(flag)参数的用法
对于标志类参数，服务器依据此参数是否存在值判断true或false. 参考`/account/login`的`wdata`参数的用法

## header中的字段
> 服务器不区分字段名称的大小写。客户端解析时建议忽略**名称**的大小写。


| 名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| cs-app-id | 即原php服务器的app_id | 是 |
| cs-token |  登录或注册成功后返回的token  | 登录后必须 |
| cs-token-expirytime | token失效时间戳 | 否（只出现在登录、注册成功时服务器返回的header中） |
| cs-device-id | imei或等同唯一识别码 | 否 |
| user-agent | 客户端信息.   | 否 |
| cs-device | 手机型号及版本信息   | 否 |
| cs-app | app型号   | 否 |
| cs-scale | 秤的型号   | 否 |

**`user-agent, cs-device, cs-app, cs-scale`将用于后台统计设备信息**

`user-agent`格式为`package.name/version (platform; device model; platfrom-version)`如,
```
com.chipsea.btcontrol.en/1.4.6 (Android;SM-A300FU;5.0.2)
btWeigh/1.4.6 (iPhone; iOS 9.2.1; Scale/2.00)
```

# 账号相关


## 修改账号普通信息(签名、显示类别、称重与长度单位、App背景) POST /account

| 名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| signature | 社区的签名  | 否 |
| `mtypes` | `weight`, `bp`, `bsl`, `food`, `exercise`, `all`之一，或前者的组合（使用逗号拼接） | 否 |
| length_unit | 长度单位  | 否 |
| weight_unit | 称重单位  | 否 |
| appbg | App背景图名称。由后面的上传资源接口返回。  | 否 |

上述参数必须至少提供一个。

```bash
curl  -v \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: c61cf98a6dcf6bc4455f1db67301a6d62cfc251e43384c52ae15fb7cccb1f1cf ' \
-H 'Content-Type: application/json' \
-X POST \
-d '{
"signature" : "好好增肥",
"mtypes":"exercise,food",
"appbg": "000dc61ec56c09c6903453059080f62f"
}' \
http://[base-api]/account
```

返回

```json
{"code":200}
```

## 获取账号信息(签名、显示类别、称重与长度单位、App背景等等) GET /account

```bash
curl  -v \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: c61cf98a6dcf6bc4455f1db67301a6d62cfc251e43384c52ae15fb7cccb1f1cf ' \
http://[base-api]/account
```

返回除密码外当前账号的所有信息:

```json
{
"code": 200,
"data": {
"haier": "15019484424",
"phone": "15019484424",
"last_login": "2017-07-12",
"signature": "好好增肥",
"days": 0,
"weight_unit": 1400,
"company_id": 15,
"qq": "",
"sina_blog": "",
"grade_id": 0,
"register_time": "2017-03-20 17:41:13",
"id": 92043,
"length_unit": 1400,
"mtypes": "exercise,food"
}
}
```


## haier登录 POST /account/login

| 名称 | 描述 |
| ------------- | :------------- |
| haier | haier的账号（手机号或用户名等） |
| access_token | qq、新浪、微信、haier的`access_token` |
| company_id | 固定为15 |
| wdata | flag: 存在时返回account,role,remind数据，否则只返回account数据  |


```bash
curl  \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'Content-Type: application/json' \
-X POST \
-d '{"haier":"18856877767", "access_token":"7976511a-09a6-44b4-a608-abcdeffffff", "company_id": 15, "wdata": "t"}' \
https://hrznc.haier.net/account/login
```

返回的角色按role.id升序排列。role.id最小的角色即为主角色。

```json
{
"code": 200,
"data": {
"account": {
"company_id": 15,
"days": 0,
"grade_id": 0,
"haier": "18856877767",
"id": 208344,
"last_login": "2018-12-05",
"length_unit": 1400,
"mtypes": "weight",
"qq": "",
"register_time": "2018-12-05 08:31:52",
"signature": "",
"sina_blog": "",
"status": 1,
"weight_unit": 1400
},
"flash_remind": [
{
"account_id": 208344,
"fri_open": 0,
"id": 10120,
"is_open": 0,
"mon_open": 0,
"once_open": 1,
"remind_time": "8:00",
"sat_open": 0,
"sun_open": 0,
"thu_open": 0,
"tue_open": 0,
"wed_open": 0
},
{
"account_id": 208344,
"fri_open": 0,
"id": 10121,
"is_open": 0,
"mon_open": 0,
"once_open": 1,
"remind_time": "12:00",
"sat_open": 0,
"sun_open": 0,
"thu_open": 0,
"tue_open": 0,
"wed_open": 0
},
{
"account_id": 208344,
"fri_open": 0,
"id": 10122,
"is_open": 0,
"mon_open": 0,
"once_open": 1,
"remind_time": "17:00",
"sat_open": 0,
"sun_open": 0,
"thu_open": 0,
"tue_open": 0,
"wed_open": 0
}
],
"remind": [
{
"account_id": 208344,
"fri_open": 0,
"id": 139723,
"is_open": 0,
"mon_open": 0,
"once_open": 1,
"remind_time": "8:00",
"sat_open": 0,
"sun_open": 0,
"thu_open": 0,
"tue_open": 0,
"wed_open": 0
},
{
"account_id": 208344,
"fri_open": 0,
"id": 139724,
"is_open": 0,
"mon_open": 0,
"once_open": 1,
"remind_time": "12:00",
"sat_open": 0,
"sun_open": 0,
"thu_open": 0,
"tue_open": 0,
"wed_open": 0
},
{
"account_id": 208344,
"fri_open": 0,
"id": 139725,
"is_open": 0,
"mon_open": 0,
"once_open": 1,
"remind_time": "17:00",
"sat_open": 0,
"sun_open": 0,
"thu_open": 0,
"tue_open": 0,
"wed_open": 0
}
],
"role": [
{
"account_id": 208344,
"birthday": "1999-09-09",
"create_time": "2018-11-05 08:32:37",
"current_state": 1,
"height": 165,
"icon_image_path": "",
"id": 1995509,
"nickname": "大庆",
"role_type": 0,
"sex": "女",
"weight_goal": "53.0"
}
]
},
"msg": "OK"
}
```

登录成功后，返回的header中会包含如下的token信息:
```java
// token
Cs-Token:8f52ed8aef48ce8ffff65a1a3372c65c57898fa85f9d44b6ba14acab09c91b69
// 失效时间
Cs-Token-Expirytime:1439552877720
```



## 获取自己或他人的总积分 GET /jifen


| 名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| account_id | 账号id  | 否. 不提供时查询当前用户，提供时查询其它用户 |


```bash
curl -v \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
'http://[base-api]/jifen?account_id=158271'
```

返回如下

```json
{"code":200,"data":10}
```

## 获取积分记录 GET /jifen/list

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| account_id | 账号ID | 否，默认为当前用户 |
| cnt | 条目数 | 否，默认10 |
| lastid | 上次最小id | 否，默认最新一条记录id |


```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
'http://[base-api]/jifen/list?account_id=158271&cnt=5&lastid=212223'
```


返回

```json
{
"code": 200,
"data": [
{
"id": 1,
"score": 10,
"source": 6,
"date": "2017-07-18",
"ts": 1500346980570,
"account_id": 158271
}
]
}
```

其中`score`为某次事件获得的分数，`source`为来源：


| source | 描述 | 得分 | 频次 |
| ------------- | :------------- | ----- | ----- |
| 101  | 首次注册登录成功 | 20 | 1 |
| 102  | 首次绑定体脂秤 | 20 | 1 |
| 103  | 首次绑定营养秤 | 20 | 1 |
| 104  | 签到 | 5 | 1次/天 |
| 105  | 打卡（无图） | 20 | 1次/周 |
| 106  | 打卡（有图） | 30 | 1次/周 |
| 107  | 评论打卡或文章 | 10 | 1次/周 |
| 108  | 上传视频并通过审核 | 30 | 1次/周 |
| 109  | 购买并确认收货 | 50 | 不限 |
| 110  | 评为达人 | 由管理员指定 | 不限 |
| 111  | 支付订单 | 支付订单时消耗的分值，为负数 | 不限 |
| 112  | 积分赠送 | 后台管理导入，分值由管理员指定 | 不限 |
| 113  | 退货退积分 | 退还原扣除的积分 | 不限 |

<!-- ``` -->
<!-- 1 称重 -->
<!-- 2 打卡 -->
<!-- 3 连打7天 -->
<!-- 4 连打21天 -->
<!-- 5 连打100天 -->
<!-- 6 打卡成达人 -->
<!-- 7 打卡参与活动 -->
<!-- 8 邀请注册 -->
<!-- ``` -->



## 申请积分兑换 PUT /jifen/coupon

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| coupon | 兑换码 | 是 |


```bash
curl  \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 3d6199a1c94d949e1736f0a8424323a177f9419a3b2b4c289635e525edf2bff5' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"coupon":"xxxxyyyyyyzzz"}' \
https://hrznc.haier.net/jifen/coupon
```

## 获取积分兑换结果列表 GET /jifen/coupon

```bash
curl  \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 3d6199a1c94d949e1736f0a8424323a177f9419a3b2b4c289635e525edf2bff5' \
-X GET \
http://localhost:3333/jifen/coupon
```

返回如下，其中

* `status`为`'accept (兑换成功), 'pending'（审核中）, 'reject'（失败）`。
* `reason`为文字描述成功或失败的原因
* `ts`申请时间

```json
{
"code": 200,
"data": [
{
"aid": 195966,
"coupon": "XXXXYYYYYYZZZ",
"id": 1949,
"reason": "",
"score": 0,
"status": "pending",
"ts": 1545615620204
}
],
"msg": "OK"
}
```



# 家庭成员

## 添加角色 PUT /role/:nickname

在当前账号下添加角色（家底成员）。一个账号最多只能包含7名成员。



注意：要上传头像时，须用表单方式提交请求。

| 名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| nickname | 昵称,在同一账号中必须唯一  | 是 |
| height | 身高(cm)  | 是 |
| birthday | 生日，格式`yyyy-MM-dd`  | 是 |
| sex | 性别: '男','女'  | 是 |
| icon | 头像  | 否  |
| weight_goal | 目标体重  | 否 |
| role_type | 整型角色类型，客户端定义  | 否，默认为0 |

```bash
curl -k -v \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'user-agent:com.chipsea.btcontrol.en/1.4.9 (Android;SAMSUNG-SM-G920A;6.0.1)' \
-H 'cs-token:3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"birthday": "1999-07-09", "sex":"男", "height": 178 }' \
'http://[base-api]/role/%2Cafda'
```



调用成功后返回`data`为`role_id`，如下，
```json
{
"code": 200,
"data": 178
}
```

## 更新角色 POST /role/:roleid
修改当前登录用户下，此`roleid`的成员属性。



**注意：**
* 要上传头像时，须用表单方式提交请求。
* 只提交需要修改的字段。例如只需修改身高时，只提交`height`字段。


| 名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| nickname | 新昵称  | 否 |
| height | 身高(cm)  | 否 |
| birthday | 生日，格式`yyyy-MM-dd`  | 否 |
| sex | 性别: '男','女'  | 否 |
| icon | 头像文件 | 否  |
| weight_goal | 目标体重  | 否 |
| weight_init | 初始体重  | 否 |
| role_type | 整型角色类型，客户端定义  | 否 |


调用成功后返回结果如下，
```json
{"code":200}
```

## 删除角色 DELETE /role/:roleid
根据`role_id`删除成员。不允许删除此账号role.id最小的角色（即主角色）。



```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
-H 'Content-Type: application/json' \
-X DELETE \
http://[base-api]/role/273
```

调用成功后返回结果如下，
```json
{"code":200}
```

## 获取角色信息 GET /role/:roleid
获取成员信息。

> 用户头像请用本接口返回的`icon_image_path`，调用`/icon/:path`下载。


| 名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| roleid | 角色ID | 是 |

调用成功后返回结果如下，
```json
{
"code": 200,
"data": {
"current_state": 1,
"sex": "男",
"weight_goal": 0,
"account_id": 99,
"icon_image_create_time": null,
"nickname": "fool",
"birthday": "1983-07-08T16:00:00Z",
"icon_image_path": "943b93c616dfe5bea181b8a5039f6372",
"sync_time": null,
"id": 173,
"create_time": "2015-09-11T02:14:16Z",
"modify_time": "2015-09-11T04:41:52Z",
"height": 188,
"period_time": null
}
}
```

## 获取当前账号下所有的角色信息 GET /roles

```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
-H 'Content-Type: application/json' \
-H 'cs-device: Android;SM-A300FU;5.0.2' \
-H 'cs-scale: Hair;1.292' \
-H 'cs-app: com.haier/2.12.2' \
-X GET \
http://[base-api]/roles
```


返回示例：

```json
{
"code": 200,
"data": [
{
"current_state": 1,
"sex": "女",
"weight_goal": "0.0",
"account_id": 161705,
"nickname": "123456",
"birthday": "2000-06-15",
"icon_image_path": "",
"id": 276476,
"create_time": "2017-07-07 17:48:38",
"role_type": 0,
"height": 160
},
{
"current_state": 1,
"sex": "男",
"account_id": 161705,
"nickname": ",afda",
"birthday": "1999-07-09",
"icon_image_path": "",
"id": 276477,
"create_time": "2017-07-14 15:19:52",
"role_type": 0,
"height": 178
}
]
}
```




## 下载资源(头像、打卡图片、App背景) GET /icon/:path

`http://[base-api]/icon/654350ea6f443e5db4d9fabc77e96a70`

返回头像图片。文件不存在时返回HTTP状态404。

文件类型可以从Response header中获取，如下
```ini
Content-Type:image/jpeg
Date:Mon, 14 Sep 2015 02:53:19 GMT
Server:Jetty(7.6.13.v20130916)
```

# 测量数据与运动、饮食记录

## 添加数据 PUT /mdata

> 本操作不具有原子性。

在当前账号下添加一条或多条测量数据。

`mdata`数组

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| `mdata` | 测量结果数组，测量结果的数据结构见下表 | 是 |


**体重测量结果字段**

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| `mtype`  | 测量类型：`weight` | 否，但建议2.0后版本提供 |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| `weight`  |  | 是 |
| `weight_time`  |  | 是 |
| axunge  | 身体脂肪率 | 否 |
| bmi  |  | 否 |
| body_age  | 正整数  | 否 |
| bone  |  | 否 |
| metabolism  | 基础代谢 | 否 |
| muscle  |  | 否 |
| viscera  | 内脏脂肪 | 否 |
| water  |  | 否 |
| r1  | 电阻值，浮点数 | 否 |
| age  | 年龄, tinyint | 否，秤数据 |
| sex  | 性别, tinyint，1或0 | 否，秤数据 |
| height  | 身高, tinyint | 否，秤数据 |
| rn8  | 电极电阻, varchar(200)，为逗号分开的浮点数 | 否，秤数据 |
| resistance  | V10电阻值，varchar(512)，电阻值字节数组经HEX编码 | 否，V10秤数据 |
| iseightr  | 是否8电极  | `y`是，其它值表示否 |



* 血压(bp)测量结果字段

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| `mtype`  | 测量类型：`bp` | 是 |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| `sys`  | 收缩压，integer | 是 |
| `dia`  | 舒张压，integer | 是 |
| hb  | 心率，integer | 否 |
| `measure_time`  |  | 是 |

* 血糖(bsl)测量结果字段

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| `mtype`  | 测量类型：`bsl` | 是 |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| `bsl`  | 血糖, float(5,2) | 是 |
| `description`  | ~~`pre-meal`:饭前，`post-meal`:饭后~~ `1`:空腹， `2`:早餐后，`3`:午饭前, `4`:午饭后, `5`:晚饭前,`6`:晚饭后 | ~~是~~ |
| `measure_time`  |  | 是 |


* 饮食(food)数组字段

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| `mtype`  | 测量类型：`food` | 是 |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| name |  | 是 |
| quantity |  | 是 |
| food_id | 食材数据库的id  | 是 |
| date | 格式为`yyyy-MM-dd HH:mm:ss`的字符串 | 是 |
| unit | `g`, `ml`或`json`对象或`json`字符串  | 是 |
| calory | kCal  | 是 |
| ftype | `breakfast`,`lunch`,`dinner`,`snacks`,`unknown`.  | 是 |
| metabolism  | 基础代谢 | 是 |

* 运动（exercise）字段

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| `mtype`  | 测量类型：`exercise` | 是 |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| name |  | 是 |
| duration | 分钟数 | 是 |
| date | 格式为`yyyy-MM-dd`的字符串 | 是 |
| ex_id |   | 是 |
| calory | kCal  | 是 |
| metabolism  | 基础代谢 | 是 |

* 课程训练（training）字段

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| `mtype`  | 测量类型：`training` | 是 |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| tid | 课程（tutorial）id | 是 |
| measure_time | 训练完成时间。格式为`yyyy-MM-dd HH:mm:ss`的字符串 | 是 |
| calory | 当前课程消耗的能量kCal  | 是 |
| metabolism  | 基础代谢 | 是 |

```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"mdata": [{"axunge": 28, "viscera": 1, "metabolism": 473, "water": 99, "weight_time": "2015-07-27 09:25:30", "role_id": 276476, "body_age": 0, "bmi": 24, "weight": 1269.0, "muscle": 33, "bone": 3.4}, {"axunge": 28, "viscera": 1, "metabolism": 473, "water": 99, "weight_time": "2015-07-27 09:29:30", "role_id": 276477, "body_age": 0, "bmi": 24, "weight": 69, "muscle": 33, "bone": 3.4, "age": 88}]}' \
http://[base-api]/mdata

血压、血糖等
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"mdata": [{"role_id": 1209, "mtype": "bp", "sys": "120", "dia": "80", "hb": "80", "measure_time": "2015-07-27 09:22:22"},{"role_id": 1209, "mtype": "bsl", "bsl": 2.22, "description": "2", "measure_time": "2015-07-27 03:22:22"}, {"mtype": "weight","axunge": 28, "viscera": 1, "metabolism": 473, "water": 99, "weight_time": "2015-07-27 09:29:30", "role_id": 1209, "body_age": 0, "bmi": 24, "weight": 69, "muscle": 33, "bone": 3.4}]}' \
http://[base-api]/mdata

配餐/饮食
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2323281aalk1136f0a8424323a177f9419a3b2b4c289635e525edf2bff5  ' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"mdata": [{"metabolism": 0,"mtype":"food","food_id":10203, "name":"雪花鱼丝羹", "quantity":100,"unit":"g","calory":32.5, "ftype":"unknown", "date":"2016-10-23 22:00:01", "role_id":354726}, {"metabolism": 0,"mtype":"food","food_id":1, "name":"小麦", "quantity":100,"unit":"g","calory":339.0, "ftype":"unknown", "date":"2016-10-23 22:00:01", "role_id":354726}]}' \
http://[base-api]/mdata



运动与饮食
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"mdata": [{"metabolism": 1228,"mtype":"food","food_id":10203, "name":"雪花鱼丝羹", "quantity":100,"unit":"g","calory":32.5, "ftype":"unknown", "date":"2016-10-23 22:00:01", "role_id":1209}, {"metabolism": 1228,"ex_id":1716, "name":"网球，双打", "duration":120,"calory":1292, "date":"2016-10-23", "role_id":1209, "mtype":"exercise"}]}' \
http://[base-api]/mdata

课程训练
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2232322c94d949e1736f0a8424323a177f9419a3b2b4c289635e525edf2bff5  ' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"mdata": [{"mtype":"training", "role_id": 354726, "tid": 3, "measure_time": "2018-09-18 16:11:02", "calory": 1024, "metabolism": 1000}]}' \
http://[base-api]/mdata
```

返回如下，
```json
{
"data": [
{
"weight_time": "2015-07-27 09:25:30",
"id": 13437,
"role_id": 3
},
{
"weight_time": "2015-07-27 09:29:30",
"id": 13438,
"role_id": 3
}
],
"code": 200
}
```

**对于称重数据**，
* 相同称重时间的数据已经存在时：会返回已存在的数据，且`exists`值为`t`：
* 存在无效数据时：返回原数据，且`invalid`字段值为`t`

```json
{
"code": 200,
"data": [
{
"upload_time": "2016-09-19T01:25:39Z",
"account_id": 6385,
"mtype": "weight",
"bone": 3.4,
"muscle": 33,
"invalid": "t",
"weight": 1269.0,
"bmi": 24,
"body_age": 0,
"role_id": 10273,
"weight_time": "2015-07-27 09:25:30",
"water": 99,
"metabolism": 473,
"viscera": 1,
"axunge": 28
},
{
"exists": "t",
"upload_time": "2016-09-19 09:25:37",
"account_id": 6385,
"mtype": "weight",
"bone": "3.4",
"scaleproperty": 1,
"muscle": "33.0",
"weight": "69.0",
"id": 912642,
"bmi": "24.0",
"body_age": 0,
"productid": 0,
"role_id": 10273,
"weight_time": "2015-07-27 09:29:30",
"water": "99.0",
"metabolism": "473.0",
"viscera": "1.0",
"axunge": "28.0"
}
]
}
```

**另外两种数据类型(bp, bsl)**数据重复时，使用新上传数据覆盖服务器数据，返回的数据的`exists`值为`u`：。

```json
{
"data": [
{
"exists": "u",
"id": 39,
"role_id": 6189,
"measure_time": "2015-06-12 10:44:00"
}
],
"code": 200
}
```





## V10解析八电极数据 POST /weight/parse



参数，与普通测量字段相比，`sex age height resistance iseightr`是必须上传的：

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| `role_id`  | 当前账号下的一使用者角色ID | 是 |
| `weight`  |  | 是 |
| `weight_time`  |  | 是 |
| axunge  | 身体脂肪率 | 否 |
| bmi  |  | 否 |
| body_age  | 正整数  | 否 |
| bone  |  | 否 |
| metabolism  | 基础代谢 | 否 |
| muscle  |  | 否 |
| viscera  | 内脏脂肪 | 否 |
| water  |  | 否 |
| r1  | 电阻值，浮点数 | 否 |
| age  | 年龄, tinyint | 是 |
| sex  | 性别, tinyint, 1或0 | 是 |
| height  | 身高, tinyint | 是 |
| resistance  | V10电阻值，varchar(512)，电阻值字节数组经HEX编码 | 否，V10秤数据 |
| iseightr  | 是否8电极. `y`是，其它值表示否  | 否 |

```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
-H 'Content-Type: application/json' \
-X PUT \
-d '{"height": 165, "sex": 1, "axunge": 28, "viscera": 1, "metabolism": 473, "water": 99, "weight_time": "2015-07-27 09:29:30", "role_id": 354726, "body_age": 0, "bmi": 24, "weight": 69, "muscle": 33, "bone": 3.4, "age": 23, "resistance": "04e804500450045004500450134f4d045004500450045004501540450450045004500450", "iseightr": "y"}' \
http://[base-api]/mdata
```

返回原数据及解析后的数据，其中`readable`为解析出的数据：

```json
{
  "code": 200,
  "data": {
    "account_id": 195966,
    "age": 23,
    "axunge": 28,
    "bmi": 24,
    "body_age": 0,
    "bone": 3.4,
    "height": 165,
    "iseightr": "y",
    "metabolism": 473,
    "muscle": 33,
    "readable": {
      "BFR": 5.1603427,
      "BMR": 1880.2731,
      "BodyAge": 15.0,
      "FC": 6.4197216,
      "FM": 3.5606365,
      "LABFR": 12.626645,
      "LASLM": 3.1259863,
      "LBM": 65.43936,
      "LLBFR": 6.2547226,
      "LLSLM": 13.325858,
      "MC": -16.711733,
      "MSW": 2.6198425,
      "PM": 14.928187,
      "RABFR": 9.264409,
      "RASLM": 3.42192,
      "RLBFR": 4.957489,
      "RLSLM": 13.80526,
      "SLM": 62.81952,
      "SMM": 43.973663,
      "Score": 59.0,
      "TF": 47.89133,
      "TFR": 69.40772,
      "TRBFR": 9.366856,
      "TRSLM": 26.712818,
      "VFR": 3.0,
      "Version": "CS_BIAD_V431",
      "WC": -10.292011,
      "WHR": 0.60276574
    },
    "resistance": "04e804500450045004500450134f4d045004500450045004501540450450045004500450",
    "role_id": 354726,
    "sex": 1,
    "viscera": 1,
    "water": 99,
    "weight": 69,
    "weight_time": "2018-07-27 09:29:30"
  },
  "msg": "OK"
}
```


## 删除数据 DELETE /mdata/:mtype/:mid-:mid-:mid-...


> 本操作具有原子性。

**注意** uri长度不能超过2083个字符。如果超过了这个长度（一次删除超过200以上的数据），建议客户端分多次调用本接口。

根据测量数据ID删除一条或多条测量数据。服务器忽略不存在的数据。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| mtype | `weight`, `bp`, `bsl`之一 | 是 |
| mid | 测量数据ID | 是 |



```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:97bed697edd584fb5eaaefb191d9c1eac643aa9844f24c03a5aad9da492f1f9b' \
-H 'Content-Type: application/json' \
-X DELETE \
http://[base-api]/mdata/610145

curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
-H 'Content-Type: application/json' \
-X DELETE \
http://[base-api]/mdata/food/9
```


## 获取全量数据 GET /mdata


获取当前账号下的测量数据，`cnt_by_days`, `cnt`, `start`只能使用其一使用。
* 传入`cnt_by_days`时，获取`end`时间前的`cnt_by_days`天数内的数据。数据结果按时间倒序排列。
* 传入`cnt`时，获取`end`时间前的`cnt`条数据。数据结果按时间倒序排列。
* 传入`start`时，获取`start`与`end`时间之间的所有数据。


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| `role_id` | 角色id。不提供时查询所有角色。不为空时将在`data`中与`mdata`同级返回。 | 否 |
| `cnt_by_days` | 返回`end`前有数据的`cnt_by_days`天里的数据 |  |
| `cnt` | 返回条目数，默认1000条  |  |
| `start` | 查询开始时间（最小时间）。格式为整型的timestamp |  |
| `end` | 查询结束时间（最大时间），默认为当前时间。格式为整型的timestamp | 否 |
| `mtype` | `weight`, `bp`, `bsl`, `food`, `exercise`, `all`之一，或前者的组合（使用逗号拼接）。**不提供时默认查询体重** | 否 |


```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: ab919ecbadeb859a03cb116567071d4be27a6976e91a44d393761c1c2c5ad364b ' \
-H 'Content-Type: application/json' \
-X GET \
'http://[base-api]/mdata?cnt_by_days=10&end=1482903675326&mtype=weight,food,exercise&role_id=41700'

curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
-H 'Content-Type: application/json' \
-X GET \
'http://[base-api]/v2/mdata?end=1470795258000&cnt=5&role_id=1209&mtype=weight'

查询最近2个有数据天里的数据：
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
-H 'Content-Type: application/json' \
-X GET \
'http://[base-api]/mdata?cnt_by_days=2&mtype=weight,food,exercise'
```

`mtype`为`all`，不传入`role_id`时，

```json
{
"data": {
"mtype": "all",
"lastsync": 1464317401,
"role_id": null,
"mdata": [
{
"axunge": "0.0",
"viscera": "0.0",
"metabolism": "0.0",
"water": "0.0",
"weight_time": "2016-04-11 10:10:52",
"role_id": 1368,
"productid": 101,
"body_age": 0,
"bmi": "20.45",
"id": 510005,
"weight": "59.1",
"muscle": "0.0",
"scaleproperty": 1,
"bone": "0.0",
"mtype": "weight",
"scaleweight": "118.2",
"account_id": 7,
"upload_time": "2016-04-11 10:10:52"
},
{
"axunge": "0.0",
"viscera": "0.0",
"metabolism": "0.0",
"water": "0.0",
"weight_time": "2016-01-21 11:41:43",
"role_id": 8,
"productid": 101,
"body_age": 0,
"bmi": "21.97",
"id": 39207,
"weight": "59.1",
"muscle": "0.0",
"scaleproperty": 1,
"bone": "0.0",
"mtype": "weight",
"scaleweight": "118.2",
"account_id": 7,
"upload_time": "2016-01-21 11:41:44"
}
]
},
"code": 200
}
```

```json
{
"code": 200,
"data": {
"mdata": [
{
"date": "2016-10-24",
"upload_time": "2016-10-24 15:48:51",
"calory": 21060,
"account_id": 566,
"unit": "g",
"mtype": "food",
"name": "麦当劳鸡柳满分加蛋",
"type": "lunch",
"id": 167,
"food_id": 25322,
"role_id": 1209,
"quantity": 100.0
},
{
"date": "2016-10-24",
"upload_time": "2016-10-24 15:56:34",
"calory": 363,
"account_id": 566,
"unit": "{\"unit_id\":14761,\"amount\":\"1.0\",\"unit\":\"个\",\"weight\":\"90.0\",\"eat_weight\":\"90.0\",\"calory\":\"362.7\"}",
"mtype": "food",
"name": "711 巧克力千层雪面包",
"type": "snacks",
"id": 968,
"food_id": 127338,
"role_id": 1209,
"quantity": 1.0
},
{
"date": "2016-10-23",
"upload_time": "2016-10-24 15:01:36",
"calory": 1292,
"account_id": 566,
"mtype": "exercise",
"name": "网球，双打",
"duration": 120,
"ex_id": 1716,
"id": 2,
"role_id": 1209
}
],
"lastsync": 1477295835,
"mtype": "food,exercise"
}
}
```

不存在测量数据时，返回的`data`为空。 返回字段说明：

| 名称 | 描述 |
| ------------- | :------------- |
| mdata | 测量结果数组 |
| lastsync | 同步时间 |



## 同步增量数据 POST /mdata

参考[同步表更新文档](http://192.168.0.72/public/mdata-sync.html)



> 用户从未同步过，即lastsync不存在时，请调用`GET /mdata`来获取数据。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| action | sync(获取新增或删除ID列表), download（依据ID下载新增数据） | 是 |

**获取差异ID（`action=sync`）时的参数**

> `2.0`版后建议使用`mtype=all`，这样客户端就只用维护一个`lastsync`

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| role_id | 不提供时查询所有角色的数据 | 否 |
| start | 测量时间最小值(inclusive) long(11)  | 是 |
| end | 测量时间最大值(exclusive) long(11) | 是 |
| lastsync | 上次同步的timestamp(8位数字，准确到秒) | 是 |
| mtype | `weight`, `bp`, `bsl`, `all`之一，或前三者的组合（使用逗号拼接）。**不提供时为`weight`**, 传入`all`时，返回所有类别。 | 否，但`2.0`版后必须提供 |

**下载差异数据(`action=download`)时的参数**

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| mids | 测量数据ID的数组 | 是 |
| mtype | `weight`, `bp`, `bsl`之一。**不提供时为`weight`** | 否，但`2.0`版后必须提供 |

本接口返回同步时间在`lastsync`之后，且测量时间在start与end间的新数据（`added`）与删除的数据(`deleted`)的ID列表

**同步(action=sync)请求示例**
```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:03703a4ad8e7408e1238b1e62c6bbce3f517237e0f3648f9aa9ecf26c0db53b7' \
-H 'Content-Type: application/json' \
-X POST \
-d '{"action":"sync", "lastsync":1451548253, "start":1446275232000, "end":1451548263790, "role_id": 8}' \
http://[base-api]/mdata

curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:03703a4ad8e7408e1238b1e62c6bbce3f517237e0f3648f9aa9ecf26c0db53b7' \
-H 'Content-Type: application/json' \
-X POST \
-d '{"action":"sync", "lastsync":0, "start":0, "end":1451548263790, "role_id":9, "mtype":"bsl,bp"}' \
http://[base-api]/mdata
```
如果此role_id不存在（通常表示此角色已经通过另一部手机删除），则返回

```json
{
"code": 111,
"msg: "原角色不存在"
}
```

不传入`role_id`，同步此账号所有角色的数据时，

`v2`返回,`deleted`或`added`为数组的数组，比`v1`接口多了数据类型。

```json
{
"data": {
"lastsync": 1463645167,
"deleted": [
[
"bp",
21280
],
[
"weight",
21175
]
],
"role_id": null,
"added": [
[
"bsl",
27906
],
[
"bp",
21744
],
[
"weight",
26647
]
]
},
"code": 200
}
```

`v1`返回：

```json
{
"code": 200,
"data": {
"lastsync": 1442372245,
"added": [
7989,
7990,
7985,
7988,
7987,
7986,
7984
],
"deleted": [
7982,
7991
]
    }
}
```

传入`role_id`，同步此角色的数据时，（即`data`中多`role_id`字段）

`v2`返回：
TODO

`v1`返回：
```json
{
  "data": {
    "lastsync": 1447728550,
    "role_id": 333,
    "deleted": [
      13632,
      13631,
      100000009591
    ],
    "added": [
      15000,
      13633,
      13651,
      13634
    ]
  },
  "code": 200
}
```


**下载新增数据示例**
```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"action":"download", "mids": [7986,7987]}' \
   http://[base-api]/mdata
```
返回如下
```json
{
  "data": [
    {
      "axunge": 0.0,
      "viscera": 0.0,
      "metabolism": 0.0,
      "water": 0.0,
      "weight_time": "2015-07-15 19:50:28",
      "role_id": 3,
      "body_age": 0,
      "bmi": 23.0,
      "id": 7985,
      "weight": 63.0,
      "muscle": 0.0,
      "bone": 0.0,
      "account_id": 2
    },
    {
      "axunge": 0.0,
      "viscera": 0.0,
      "metabolism": 0.0,
      "water": 0.0,
      "weight_time": "2015-07-15 19:50:28",
      "role_id": 3,
      "body_age": 0,
      "bmi": 23.0,
      "id": 7987,
      "weight": 63.0,
      "muscle": 0.0,
      "bone": 0.0,
      "account_id": 2
    },
    {
      "axunge": 0.0,
      "viscera": 0.0,
      "metabolism": 0.0,
      "water": 0.0,
      "weight_time": "2015-07-15 19:50:28",
      "role_id": 3,
      "body_age": 0,
      "bmi": 23.0,
      "id": 7988,
      "weight": 63.0,
      "muscle": 0.0,
      "bone": 0.0,
      "account_id": 2
    }
  ],
  "code": 200
}
```


## APP数据同步流程

参考[同步表更新文档](http://192.168.0.72/public/mdata-sync.html)

* 首次启动

```
                        APP启动
                           |
                   GET /roles 获取角色
                           |
       GET /mdata {end(当前时间), start(建议为当前月份-2的1号)}
                           |
        保存返回的测量结果与lastsync, start, end
                           |
                  启动后台进程合并lastsync记录
```

* 再次进入APP

```
                        APP启动
                           |
                   GET /roles 获取角色，移除已删除角色
                           |
         (1)判断是否存在待上传/删除数据，并调用PUT/DELETE /mdata接口
                           |
      (2)POST /mdata {action=sync, lastsync, end(当前时间),start}
                           |
         保存返回的测量结果与lastsync, start, end
         按服务器ID(deleted字段)删除本地数据；调用（3）获取新增数据
         更新csb_sync_log表, 查询条件（account_id, role_id, lastsync, start）
                           |
      (3)POST /mdata {action=download, mids}获取服务器新增数据
```

说明
* 已删除角色的数据不会出现在同步结果中
* (1), (2)可异步执行。在(3)下载数据前，须先按（2）返回的ID过滤本地已经存在的数据
* 使用当前时间作为请求参数的`end`时间
* 分角色同步数据（即传入roleid)，便于本地管理，也便于正理处理删除角色的场景
* 本地`lastsync`如果有多个时，取最小值传入
* 合并新增数据时，可依据返回结果中的`id`判断本地是否存在此记录。


客户端数据保存：
* 测量数据表额外字段：`isdelete`, 标示待删除数据
* 测量数据同步表(`csb_sync_log`)：`account_id`, `role_id`, `lastsync`, `start`, `end`

## 获取统计数据 GET /mdata/stats



> **周报表取时间段内所有数据作统计，不如v1版按天统计后再计算。**

查询客户端指定日期段内的数据。[Java解析示例](http://bitbucket.org/augustusfield/mdata-client)

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | :--------: |
| mtype | `weight`, `bp`, `bsl`, `food`, `exercise`, `all`之一。 | 是 |
| start | 开始时间戳  | 是 |
| end | 开始时间戳，(exclusive）  | 是 |
| role_id | 角色ID  | 是 |
| ptype | `1`:天，`2`：周,`3`：月  | 是 |


**参数设置：**例如查询最近n天时
* `end`设为明天的0点的时间戳
* `start`设为今天0点的时间戳减去`((n-1)*3600*24*1000)`
* 翻页时，`end`设为上次的`start`,`start`设为上次的`start`减去`(n*3600*24*1000)`

**返回数据说明**
* `ptype=1` 返回列表的key为`yyyyMMdd`格式, 
* `ptype=2` 返回列表的key为`w`格式, 即当前年的周数。周数从1开始，周日为周的开始。**使用周数查询时，end与start的差值必须小于1年。**Java可使用`Calendar`类计算`start`与`end`参数。见下文的Java示例。
* `ptype=3` 返回数据为`yyyyMM`格式的列表, 


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:27c08ee95f277dd15930f2cc60eb56028bd6cc603ac64ebdb3d52d97948e9f30' \
 -H 'Content-Type: application/json' \
 -X GET \
'http://[base-api]/mdata/stats?start=20160605&end=20160611&role_id=1209&period=day&offset=8'

curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:aef47f975a0afd800bfd3418db227fb4d9fd886d3f784fe9b0ed07285f2eadf7' \
 -H 'Content-Type: application/json' \
 -X GET \
 -d '{"start":"0", "end": "1464243354000", "ptype":"3", "role_id":"144", "mtype": "bp,weight"}'  \
http://[base-api]/mdata/stats

curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:aef47f975a0afd800bfd3418db227fb4d9fd886d3f784fe9b0ed07285f2eadf7' \
 -H 'Content-Type: application/json' \
 -X GET \
 -d '{"start":"1462895999000", "end": "1465315199000", "ptype":"1", "role_id":"1209", "mtype": "bp,weight,bsl"}'  \
http://[base-api]/mdata/stats

curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:aef47f975a0afd800bfd3418db227fb4d9fd886d3f784fe9b0ed07285f2eadf7' \
 -H 'Content-Type: application/json' \
 -X GET \
 -d '{"start":"1459008000000", "end": "1465833600000", "ptype":"2", "role_id":"1209", "mtype": "bp,weight,bsl"}'  \
http://[base-api]/mdata/stats

curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:aef47f975a0afd800bfd3418db227fb4d9fd886d3f784fe9b0ed07285f2eadf7' \
 -H 'Content-Type: application/json' \
 -X GET \
 'http://[base-api]/mdata/stats?mtype=weight&role_id=1209&start=1458576000000&ptype=2&end=1465833600000'

运动与饮食
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd ' \
 -H 'Content-Type: application/json' \
 -X GET \
 'http://[base-api]/mdata/stats?mtype=food,exercise&role_id=1209&start=1458576000000&ptype=1&end=1477374585000'
```

计算方式说明：
> 忽略值为0的数据
> 某字段所有测量结果都为无效值时，此字段的均值为空，且不返回数据。

返回如下，其中`nums`为此时间段的总测量次数；

```json
{
  "data": {
    "bp": null,
    "weight": {
      "201510": {
        "bmi": 2.75,
        "weight": 7.98,
        "nums": 4
      },
      "201509": {
        "axunge": 1.07,
        "viscera": 99.99,
        "metabolism": 28.02,
        "water": 73.82,
        "bmi": 1.95,
        "weight": 5.85,
        "nums": 255,
        "muscle": 44.36,
        "bone": 1.19
      }
    }
  },
  "code": 200
}
```

`ptype=2`的返回示例：

```json
{
  "data": {
    "bsl": {
      "20": {
        "nums": 1,
        "bsl": {
          "max": 4.5,
          "min": 4.5
        }
      },
      "21": {
        "nums": 7,
        "bsl": {
          "max": 4.5,
          "min": 2.5999999046325684
        }
      },
      "22": {
        "nums": 5,
        "bsl": {
          "max": 6.400000095367432,
          "min": 0.5
        }
      },
      "23": {
        "nums": 5,
        "bsl": {
          "max": 6.400000095367432,
          "min": 0.10000000149011612
        }
      }
    },
    "bp": {
      "24": {
        "nums": 3,
        "hb": {
          "max": 58,
          "min": 58
        },
        "dia": {
          "max": 76,
          "min": 76
        },
        "sys": {
          "max": 118,
          "min": 118
        }
      }
    },
    "weight": {
      "24": {
        "axunge": 26.1,
        "metabolism": 1285.0,
        "water": 50.4,
        "bmi": 21.2,
        "weight": 58.4,
        "nums": 2,
        "muscle": 32.0,
        "bone": 2.2
      }
    }
  },
  "code": 200
}
```

运动与饮食:

```json
{
  "code": 200,
  "data": {
    "exercise": {
      "20160910": {
        "calory": 645,
        "metabolism": 1305,
        "nums": 1
      },
      "20160907": {
        "calory": 814,
        "metabolism": 1339,
        "nums": 4
      }
    },
    "food": {
      "20160913": {
        "calory": 112968,
        "metabolism": 1335,
        "nums": 5
      },
      "20160907": {
        "calory": 77955,
        "metabolism": 1389,
        "nums": 4
      }
    }
  }
}
```

**附：季报表start/end参数计算方法**

```java
// 计算最近12周的[start,end)时间段
final int nWeeks  = 12;
Calendar cal = Calendar.getInstance(); // 手机端采用默认时区
System.out.println(cal.get(Calendar.DAY_OF_WEEK));
int weekOffset = 0 - cal.get(Calendar.DAY_OF_WEEK) ;
// 计算结束时间：明天0点
cal.set(Calendar.HOUR_OF_DAY, 0);
cal.set(Calendar.MINUTE, 0);
cal.set(Calendar.SECOND, 0);
cal.set(Calendar.MILLISECOND, 0);
cal.add(Calendar.DATE, 1);
final long end = cal.getTimeInMillis();
System.out.println("end:   "+cal.getTime() + " : " + end);

// 计算开始时间
cal.add(Calendar.WEEK_OF_YEAR, 1-nWeeks );
cal.add(Calendar.DATE, weekOffset);
final long start = cal.getTimeInMillis();
System.out.println("start: "+cal.getTime() + " : " + start);
```

# 食物与运动

## 查找食物 GET /search/food

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| term | 关键字  | 是 |
| cnt | 条目数，最大100  | 否 |
| lastid | 上次最大id  | 否 |


```bash
curl -k  \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68  ' \
 -H 'Content-Type: application/json' \
 -X GET \
  'http://[base-api]/search/food?term=米&cnt=2'
```

返回按ID排序的搜索结果。翻页时，使用最后一条记录的ID（也是最大值）作为lastid，继续搜索。
```json
{
  "code": 200,
  "data": [
    {
      "niacin": "1.9",
      "is_liquid": false,
      "natrium": "3.8",
      "calory": "347.0",
      "copper": "0.3",
      "zinc": "1.7",
      "selenium": "2.2",
      "name": "稻米",
      "type": "谷薯芋、杂豆、主食",
      "fat": "0.8",
      "magnesium": "34.0",
      "calcium": "13.0",
      "kalium": "103.0",
      "brand": "",
      "iron": "2.3",
      "cholesterol": "",
      "weight": "100",
      "status": 1,
      "id": 31,
      "thumb_image_url": "http://s.boohee.cn/house/food_mid/m_1160617508140.jpg",
      "fiber_dietary": "0.7",
      "lactoflavin": "0.1",
      "health_light": 1,
      "code": "daomi_junzhi",
      "vitamin_a": "",
      "iodine": "",
      "usedegree": null,
      "vitamin_e": "0.5",
      "protein": "7.4",
      "phosphor": "110.0",
      "vitamin_c": "",
      "units": null,
      "carotene": "",
      "manganese": "1.3",
      "carbohydrate": "77.2",
      "thiamine": "0.1"
    },
    {
      "niacin": "1.3",
      "is_liquid": false,
      "natrium": "2.4",
      "calory": "345.0",
      "copper": "0.2",
      "zinc": "1.5",
      "selenium": "2.5",
      "name": "粳米(标一)",
      "type": "谷薯芋、杂豆、主食",
      "fat": "0.6",
      "magnesium": "34.0",
      "calcium": "11.0",
      "kalium": "97.0",
      "brand": "",
      "iron": "1.1",
      "cholesterol": "",
      "weight": "100",
      "status": 1,
      "id": 32,
      "thumb_image_url": "http://s.boohee.cn/house/food_mid/m_1160662861265.jpg",
      "fiber_dietary": "0.6",
      "lactoflavin": "0.1",
      "health_light": 1,
      "code": "jingmi_biaoyi",
      "vitamin_a": "",
      "iodine": "",
      "usedegree": null,
      "vitamin_e": "1.0",
      "protein": "7.7",
      "phosphor": "121.0",
      "vitamin_c": "",
      "units": null,
      "carotene": "",
      "manganese": "1.4",
      "carbohydrate": "76.8",
      "thiamine": "0.2"
    }
  ]
}
```


## 获取食物详情 GET /food

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 食物ID  | 是 |


```bash
curl -k  \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
 -H 'Content-Type: application/json' \
 -X GET \
  'http://[base-api]/food?id=122'
```

```json
{
  "code": 200,
  "data": {
    "niacin": "0.7",
    "natrium": "18.6",
    "calory": "67.0",
    "copper": "0.1",
    "zinc": "0.2",
    "selenium": "0.2",
    "name": "酸豆奶",
    "type": "坚果、大豆及制品",
    "fat": "1.2",
    "magnesium": "16.0",
    "calcium": "32.0",
    "kalium": "70.0",
    "brand": "",
    "iron": "0.4",
    "cholesterol": "",
    "weight": "100",
    "status": 1,
    "id": 122,
    "thumb_image_url": "http://s.boohee.cn/house/upload_food/2008/8/30/user_mid_129095_1220071993.jpg",
    "fiber_dietary": "",
    "lactoflavin": "",
    "health_light": 1,
    "code": "suandounai",
    "vitamin_a": "",
    "iodine": "",
    "vitamin_e": "1.1",
    "protein": "2.2",
    "phosphor": "22.0",
    "vitamin_c": "",
    "carotene": "",
    "manganese": "0.1",
    "carbohydrate": "11.8",
    "thiamine": "0.1"
  }
}
```

## 查找运动 GET /search/exercise

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| term | 关键字  | 是 |
| cnt | 条目数，最大100  | 否 |
| lastid | 上次最大id  | 否 |


```bash
curl -k  \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
 -H 'Content-Type: application/json' \
 -X GET \
  'http://[base-api]/search/exercise?term=步&cnt=2'
```

返回

``` json
{
  "code": 200,
  "data": [
    {
      "id": 79,
      "name": "徒步",
      "met": 6.0,
      "type": "户外运动",
      "status": 1,
      "usedegree": 1
    },
    {
      "id": 1305,
      "name": "步行旅游",
      "met": 3.5,
      "type": "休闲娱乐",
      "status": 1,
      "usedegree": 0
    }
  ]
}
```

## 获取常用食物 GET /popular/food

**响应数据较大，请务必使用`Accept-Encoding: gzip`**

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://[base-api]/popular/food' -o  /dev/null
```

返回结果参考[/search/food](#查找食物 GET /search/food)


## 获取常用运动 GET /popular/exercise

**响应数据较大，请务必使用`Accept-Encoding: gzip`**

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 2dae556ecf6d207181ffd019d048ef01b6ed9e207e714937815fab290fa420fd  ' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://[base-api]/popular/exercise ' -o  /dev/null
```

返回结果参考[/search/exercise](#查找运动 GET /search/exercise)


# 课程与训练


## 获取所有课程标签 /tutorial/categories

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://[base-api]/tutorial/categories'
```

返回的data为现有标签列表。

```json
{"code":200,"msg":"OK","data":["有氧","力量"]}
```


## 获取课程列表 GET /tutorial

同一课程可包含多个视频，课程与标签为多对多关系。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| tag | 标签名  | 否，不提供时返回所有课程 |
| fav | y:只返回用户收藏课程，其它值返回所有课程  | 否 |

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://45.35.13.168:8080/tutorial'
```

返回字段说明：

| 名称 | 描述 |
| ------------- | :------------- |
| id | 课程ID  |
| title | 标题  | |
| calory | 能量消耗（kCal） |
| dir | 课程目录 |
| videos | 视频路径相对列表，用逗号开 |
| reqdev | 是（y）否（n）需要器材 |
| cover | 封面图片相对路径 |
| duration | 总时长（秒） |
| advice | 训练建议 |
| warning | 注意事项 |
| audience | 适合人群 |
| tag | 课程所在类别，用逗号分开 |
| ts | 课程上传时间 |
| fav | y:已收藏，n：未收藏 |


返回如下，

**常见问题链接，可以通过1）`videos`是否以http:或https:开头，或2）`dir`是否为空来区分视频与链接。**


```json
{
  "code": 200,
  "data": [
      {
      "advice": "",
      "audience": "所有",
      "calory": 0.0,
      "cover": "998878e37ee2257087e3adf4ae75a2bc",
      "dir": "",
      "duration": 0,
      "fav": "n",
      "id": 10000,
      "rqdev": "n",
      "tag": "运动",
      "title": "常见问题",
      "ts": 0,
      "videos": "http://47.93.10.106:8080/faq/question.html",
      "warning": ""
    },
    {
      "advice": "无",
      "audience": "所有人群",
      "calory": 0.0,
      "cover": "998878e37ee2257087e3adf4ae75a2bc",
      "dir": "20181124171210",
      "duration": 56,
      "fav": "n",
      "id": 31,
      "rqdev": "n",
      "tag": "发现",
      "title": "四电极",
      "ts": 1543287729390,
      "videos": "fd4be85806d217cb6e1ba775952a375c",
      "warning": "无"
    }
  ],
  "msg": "OK"
}
```

## 下载视频，课程封面图片 GET /res/:path

其中的`path`由上一接口返回。客户端需要能处理302跳转。

```bash
'http://[base-api]/res/8cdf0434337568ec67d1f6eff1dc2de0' 
```

如

`http://45.35.13.168:8080/res/13586f804f8d1e3cf9fc108dd41afa85`


## 下载视频采样图

### 阿里OSS

**阿里视频截帧不支持mpg格式。**

在链接后拼接如下参数下载，参考 [视频截帧](https://help.aliyun.com/document_detail/64555.html?spm=a2c4g.11174359.6.1173.5a875241dUjhlg)

```
?x-oss-process=video/snapshot,t_7000,f_jpg,w_800,h_600,m_fast
```

拼接好如下：

```
http://45.35.13.168:8080/res/65ea8d57a75145ef71b2b23e21f69407?x-oss-process=video/snapshot,t_7000,f_jpg,w_800,h_600,m_fast
```




### 七牛云的方法(已停用)

使用上一接口拼接好视频链接，并其后加下取帧图参数，
`?vframe/png/offset/0/w/480/h/480%7CimageView2/1/w/480/h/480`即可获取
视频的采样图，即单个视频封面图。如下

`http://45.35.13.168:8080/res/8cdf0434337568ec67d1f6eff1dc2de0?vframe/png/offset/0/w/600/h/900%7CimageView2/1/w/300/h/300`

详细说明可查看七牛云文档: https://developer.qiniu.com/dora/manual/1313/video-frame-thumbnails-vframe

## 收藏课程 POST /tutorial/fav

**收藏常见问题无效**

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| tid |  课程ID | 是 |
| fav |  y：收藏，n：取消收藏 | 是 |



```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498 ' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"tid":"4", "fav":"n"}' \
 http://[base-api]/tutorial/fav
```



# 应用消息

应用消息由后台管理员发送。

## 获取应用消息 GET /app/msg

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| status | 状态，read已读，unread，未读  | 否，不提供时返回所有消息 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498 ' \
 -H 'Content-Type: application/json' \
 -X GET \
 http://[base-api]/app/msg
```

返回如下

```json
{
  "code": 200,
  "data": [
    {
      "id": 4,
      "mid": 4,
      "msg": "You can group conditions with parentheses. When you are checking if a field is equal to another, you want to use OR . For example WH",
      "status": "unread",
      "title": "and"
    },
    {
      "id": 3,
      "mid": 3,
      "msg": "爱情来得快去得也快，只有猪肉卷是永恒的。\n",
      "status": "unread",
      "title": "爱情来得快去得也快，只有猪肉卷是永恒的。"
    }
  ],
  "msg": "OK"
}
```


返回字段说明：

| 名称 | 描述 |
| ------------- | :------------- |
| id | 消息ID  |
| title | 标题  | |
| msg | 消息正文  | |
| ts | 发送时间 |
| status | `unread`：未读，`read`：已读 |


## 修改应用消息状态，标记为已读 POST /app/msg

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 消息id，由上一接口返回  | 是 |
| status | 状态，read已读，unread，未读  | 是 |

修改给他人的消息会返回成功，但操作无效。


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498 ' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"id":"13", "status":"read"}' \
 http://[base-api]/app/msg
```

返回如下


```json
{"code":200,"msg":"OK","data":[1]}
```


## 删除应用消息 DELETE /app/msg

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 消息id  | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498 ' \
 -H 'Content-Type: application/json' \
 -X DELETE \
-d '
{"id": 26} 
'  \
'http://[base-api]/app/msg'
```

# 广播

## 第三方推送的数据结构

通过Jpush推送的数据结构如下：

> 推送内容`extras`里的`id`字段为下文的广播ID(`bid`)，可用于获取评论数，点赞数等。

**notification**
```json
{
  "alert": "jlj",
  "ios": {
    "extras": {
      "categories": "3",
      "id": 0,
      "preview": true,
      "uri": "9adCj3CZlsyv79ipbi\/1468464839480.html",
      "ts": 1468464839490,
      "cover": "9adCj3CZlsyv79ipbi\/cover.jpg",
      "title": "jlj"
    }
  },
  "android": {
    "extras": {
      "categories": "3",
      "id": 0,
      "preview": true,
      "uri": "9adCj3CZlsyv79ipbi\/1468464839480.html",
      "ts": 1468464839490,
      "cover": "9adCj3CZlsyv79ipbi\/cover.jpg",
      "title": "jlj"
    }
  }
}
```

**message**
```json
{
  "extras": {
    "categories": "3",
    "id": 0,
    "preview": true,
    "uri": "9adCj3CZlsyv79ipbi\/1468464839480.html",
    "ts": 1468464839490,
    "cover": "9adCj3CZlsyv79ipbi\/cover.jpg",
    "title": "jlj"
  },
  "msg_content": "jlj"
}
```


## 获取类别列表 GET /public/bdcategories

返回字段说明：

| 名称 | 描述 |
| ------------- | :------------- |
| id | 类别ID  |
| title | 中文名  | |
| title_en | 英文名 |
| bgcolor | 16进制背景色 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -X GET \
 'http://[base-api]/public/bdcategories'
```

返回如下，
```json
{
    "code": 200,
    "data": [
        {
            "bgcolor": "3d85c6",
            "title_en": "Slimming",
            "title": "减肥",
            "id": 1
        },
        {
            "bgcolor": "3d85c6",
            "title_en": "Sport",
            "title": "运动",
            "id": 2
        },
        {
            "bgcolor": "ff9900",
            "title_en": "Diet",
            "title": "饮食",
            "id": 3
        }
    ]
}
```


## 获取广播文章列表 GET /broadcastlist/:companyid



| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| companyid | 用户公司ID | 是 |
| end | 查询结束时间的整型timestamp（即最大值exclusive），默认为当前时间  | 否 |
| categories | 数字组成的数组，表示类别，不传时取所有数据 | 否 |
| sex | 性别 男/女 | 是 |
| cnt | 条目数  | 是 |

```bash
curl -k  -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'  -H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f'  -X GET  -d '{"end": "1443508301000", "cnt":2}'   'http://[base-api]/broadcastlist/1?categories\[\]=2&categories\[\]=1'


curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'  'http://[base-api]/broadcastlist/15'

curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'  'http://[base-api]/broadcastlist/1?sex=女&end=9223372036854775807&cnt=2'

curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' -X GET  'http://localhost:6080/broadcastlist/15?sex=女&end=1493282059114&cnt=2'

curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:8777742756711311636490623598956559740857577060728149792783599498' \
-X GET  'http://localhost:6080/broadcastlist/15?&cnt=2'

```

使用返回的`cover`与`uri`下载相应的封面图或文章。`nlikes`, `ncomments`分别表示like数与评论数。`fav`为`y`时表示当前账号已收藏。

```json
{
  "code": 200,
  "data": [
    {
      "cover": "9ZlJIJG3cDChagyAWO/cover.jpg",
      "title": "基础代谢，你真的了解吗",
      "ts": 1534477420622,
      "nlikes": 3,
      "categories": "5",
      "id": 1647,
      "pv": 8,
      "uri": "9ZlJIJG3cDChagyAWO/1534477420592.html",
      "ncomments": 0,
      "buid": "SIUHW8AWSTOnCMQVJDXf4w=="
    },
    {
      "cover": "9Zhm9P2OsS4SwrqZSy/cover.jpg",
      "title": "据说这是一个可以长期坚持的减肥法！",
      "ts": 1534330090061,
      "nlikes": 5,
      "categories": "5",
      "id": 1646,
      "pv": 6,
      "uri": "9Zhm9P2OsS4SwrqZSy/1534330090034.html",
      "ncomments": 1,
      "buid": "kwvinTtEQPiDoVDus5xnQg=="
    }
  ]
}
```

## 更新浏览数 /pv/buid/:buid

其中路径参数`buid`由上一接口`/broadcastlist/:companyid`返回。

文章首次下载时会将阅读计数加1。后续用户点击时，可调此接口增加计数。


## 获取文章评论数 GET /ncomments/:bid

```bash
curl -v -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'   'http://[base-api]/ncomments/184'
```

返回
```json
{"code":200,"data":{"bid":"184","ncomments":0}}
```

## 获取文章Like数 GET /nlikes/:bid


```bash
curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'   'http://[base-api]/nlikes/184'
```

```json
{"code":200,"data":{"bid":"184","nlikes":0}}
```

## 获取封面或文章 GET /article/:uri

例如：
* 下载封面URL：`http://[base-api]/article/1443082742756/cover.png`
* 下载文章URL：`http://localhost:6080/article/9ZlJIJG3cDChagyAWO/1534477420592.html`

## 获取评论 GET /acomment/:bid



| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| bid | 文章ID，由`GET /broadcastlist/:companyid`接口返回 | 是 |
| lastid | 上次最后一条评论id。将返回此id之前的评论 | 否 |
| cnt |  条目数，默认10 | 否 |

```bash
curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-X GET \
'http://[base-api]/acomment/213'
```

返回评论列表:其中`commenter_icon`为[评论人头像路径](#下载头像 GET /icon/:path)，`commenter_nickname`为评论人昵称。

```json
{
  "data": [
    {
      "content": "\u5f88\u597d",
      "ts": 1463642597559,
      "commenter_icon": " ",
      "commenter_nickname": "ghg ",
      "role_id": 273,
      "account_id": 122,
      "bid": 213,
      "id": 2
    },
    {
      "content": "\u5341\u5206\u65e0\u804a",
      "ts": 1463642580944,
      "commenter_icon": " ",
      "commenter_nickname": "ghg ",
      "role_id": 273,
      "account_id": 122,
      "bid": 213,
      "id": 1
    }
  ],
  "code": 200
}
```

## 添加评论 PUT /acomment/:bid



| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| bid | 文章ID，由`GET /broadcastlist/:companyid`接口返回 | 是 |
| content | 评论内容 | 是 |

> 自动使用当前用户第一角色的昵称与头像作为评论人的昵称与头像。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
 -H 'Content-Type: application/json' \
 -X PUT \
 -d '{"content":"很好"}' \
   http://[base-api]/acomment/213
```

返回评论id，
```json
{"code":200,"data":1}
```

## Toggle Like GET /alike/toggle/:bid



| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| bid | 文章ID，由`GET /broadcastlist/:companyid`接口返回 | 是 |

```bash
curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
-X GET \
'http://[base-api]/alike/toggle/213'
```

## 查询是否点赞 GET /alike/:bid

当前用户已经对本文章点赞时返回的data为数字id，否则为空。

```bash
curl -k -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
-X GET \
'http://[base-api]/alike/213'
```

已点赞时返回如下
```json
{"code":200,"data":5}
```

## 添加收藏 PUT /afav/:bid


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| bid | 文章ID，由`GET /broadcastlist/:companyid`接口返回 | 是 |


## 获取收藏 GET /afav

按ID倒序，分页获取收藏的文章。每页10条，页码从1开始。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| page | 页码 | 是 |


## 获取收藏数 GET /afav/count

```bash
curl -k -v -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
-X GET \
'http://[base-api]/afav/count'
```

返回当前用户收藏的文章数量
```json
{"code":200,"data":0}
```

## 删除收藏 DELETE /afav/:bid

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| bid | 文章ID，由`GET /broadcastlist/:companyid`接口返回 | 是 |


# 其它

## 上传日志 PUT /log/app

```bash
curl -k  -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/gzip' \
 -H 'Device-Id: fake' \
 -X PUT \
 --data-binary "@/home/garfield/projects/clojure/chips/resources/sample-log-app.txt.gz" \
  http://[base-api]/log/app
```

## 获取最新APP信息 GET /latestapp

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| platform | `ios`或`android`  | 是 |
| pkg | 包名，如`com.chipsea.btcontrol`  | 是 |
| version | 当前版本号  | 否 |

**对于`version`参数：**
* 不传入时，返回最新版本的信息;
* 提供时，若存在新版本则返回新版本的信息，否则返回空。

```bash
curl -k  -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
'http://[base-api]/latestapp?platform=android&version=1.3.0&pkg=com.chipsea.btcontrol'
```

有更新时响应如下：

```json
{
  "data": {
    "required": "n",
    "version": "1.4.8",
    "pkg": "com.chipsea.btcontrol",
    "url": "china_android_1.4.8_btWeigh_1.4.8.apk",
    "id": 41,
    "system_version": "android",
    "company_id": 1,
    "upgrade_time": "2016-06-20T19:33:52Z",
    "region": "china",
    "mu_version": "",
    "server": "chips",
    "content": "\u4f18\u5316toast\u63d0\u793a"
  },
  "code": 200
}
```


## APK下载 GET /btweigh/apk/:url
其中路径参数`url`为`/latestapp`返回的值。

```bash
http://[base-api]/btweigh/apk/fake.apk
```

## 获取公司信息 GET /company/:company_id



根据`company_id`获取公司信息，`company_id`的值必须是整数。

本接口返回的静态资源可通过`/logo/:path`接口下载。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X GET \
 'http://[base-api]/company/1'
```

```json
{
    "code": 200,
    "data": {
        "owner_name": "卢国健",
        "current_state": 1,
        "address": "广东省深圳市南山区南海大道1079-1花园城数码大厦",
        "slogan": " ",
        "owner_id_nmuber": null,
        "owner_sex": "男",
        "organization_number": null,
        "logo_path": "075b5c402407a66f20f191bf936aaafe",
        "phone": "0755-861692431",
        "name": "芯海科技有限公司",
        "owner_origin_place": null,
        "business_license_number": null,
        "sync_time": "2015-07-09T09:09:20Z",
        "id": 1,
        "multilang_desc": "{\"fr\":{\"name\":\"daslfj\",\"address\":\"dasfljl\",\"phone\":\"dfaslj\",\"contact\":\"dfalsjl\",\"desc\":\"fdalsj\"},\"es\":{\"name\":\"dsaf\",\"address\":\"jlkj\",\"phone\":\"lkjklj\",\"contact\":\"lkjlkj\",\"desc\":\"各可\"},\"zh\":{\"desc\":\"lkjkljlk\",\"contact\":\"ljlkj\",\"name\":\"okok\",\"address\":\"深圳\",\"phone\":\"12345678\"},\"en\":{\"name\":\"klkl\",\"address\":\"kjlk\",\"phone\":\"ljklkjk\",\"contact\":\"lkjlkj\",\"desc\":\"kljlkj\"}}",
        "create_time": "2015-09-10T01:23:22Z",
        "url": "www.tookok.cn",
        "short_intr": "深圳市芯海科技有限公司（简称芯海科技）是一家专业从事模数、数模混合集成电路设计的高新技术企业。公司成立于2003年，总部位于中国集成电路深圳产业化基地---深圳市高新软件园区，是国家认定的高新技术企业，被深圳市政府认定为第一批自主创新龙头企业和15家重点集成电路设计企业之一，是集成电路产学研联盟、中国衡器协会等机构的重要成员。",
        "owner_address": null,
        "owner_birthday": null,
        "modify_time": "2015-09-10T01:23:22Z"
    }
}
```

## 下载公司LOGO，启动页等 GET /logo/:path

注意启动页URI格式为`'/logo/cbootimg-' + company_id + '/' + ratio`，
示例：`http://[base-api]/logo/cbootimg-1/4-3`


## 获取产品信息 GET /product/:productid/:language


`language`为两个字符的iso639-1编码。

不传入`language`时，返回所有语言信息。**注意：**不传入`language`时，不能传入`productid`后的斜线。

```bash
curl -k \
  -X GET \
 'http://[base-api]/product/1440463961/zh'
```

返回如下（产品不存在时，data为空，语言不存在时，对应的语言为null）。

> 秤的logo路径，即`logo_path`为此产品对应的logo，若不存在，则取相应厂家的logo。

```
{
    "code": 200,
    "data": {
        "company_id": 3,
        "logo_path": "6446b84213265ac60da92c4e44358a57",
        "product_model": "",
        "product_desc": "描述",
        "zh": {
            "desc": "",
            "contact": "",
            "name": "厂家名",
            "address":地址",
            "phone": "0574-63860929"
        }
    }
}
```

## 更新称重提醒 POST /config/reminder


根据ID及account_id更新称重提醒。可只传需要更新的字段(所有字段见`csb_weight_remind`表)。


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| reminders | 数组，每个数组中必须包含id字段，且(`remind_time`)时间格式为HH:mm | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"reminders":[{"id":4, "remind_time":"11:15"},{"id":5,"remind_time":"13:24", "wed_open":1}]}' \
 http://[base-api]/config/reminder
```

成功后返回

```json
{"code":200}
```


## 更新炫彩提醒 POST /config/flash/reminder

同更新普通称重提醒 

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| reminders | 数组，每个数组中必须包含id字段，且(`remind_time`)时间格式为HH:mm | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 5c028f354a5ed532930ca65e449382b08004ad25bb6749669a7d645db80d0df2 ' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"reminders":[{"id":4, "remind_time":"11:15"},{"id":5,"remind_time":"13:24", "wed_open":1}]}' \
 https://hrznc.haier.net/config/flash/reminder
```

成功后返回

```json
{"code":200}
```



## 更新称重单位 POST /config/unit

> 称重单位在登录时随账号信息返回。
> ***注意：称重单位为1403时，iOS版1.4.1-4获取到的是1400***


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| length | 长度单位/1400:m  1401:inch | 是 |
| weight | 质量单位/1400:kg 1401:磅 1402:斤 1403: 英石 | 是 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
 -X POST \
 -d '{"length":"1401", "weight":1401}' \
   http://[base-api]/config/unit
```

成功后返回

```json
{"code":200}
```

## 重置显示的测量类别 PUT /config/mtypes

用法与`/config/unit`相同。即在登录时获取。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| mtypes | `weight`, `bp`, `bsl`, `all`之一或前三者用逗号组合。 | 是 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token:5e48f4019f26e26b5bddeb7fd23e83f78c91ee7863194a49b858c3492dd984d3' \
 -X PUT \
 -d '{"mtypes":"weight,bp"}' \
   http://[base-api]/config/mtypes
```

成功后返回

```json
{"code":200}
```

## 设置签名 PUT /config/signature

设置的签名在登录的时候返回。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | --------: |
| signature | 打卡的签名 | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token:6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f' \
 -X PUT \
 -d '{"signature":"safdk"}' \
   http://[base-api]/config/signature
```

## 提交用户反馈 POST /feedback


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| content | 反馈内容  | 是 |
| contact | 联系方式 | 未登录用户必须提供 |
| fid | 主题ID | 否。参与话题时提供 |

**说明**
* `fid`存在时，必须先登录
* `fid`不存在时，必须先登录或传入`contact`

```bash
未登录用户新建反馈：
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"content":"很好", "contact":"18575112888"}' \
   http://[base-api]/feedback

已登录用户新建反馈：
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"content":"1、进入育婴百科 2、用手左右滑动顶部banner图片 3、发现图片切换顺序有手势顺序相反 4、且是白点不是绿点："}' \
   http://[base-api]/feedback
   
用户回复：
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 920d5c5bddbf1c19ccf749a955260199259f1b197c284ccba0952af5de6fe6dc ' \
 -H 'Content-Type: application/json' \
 -X POST \
 -d '{"content":"1、进入育婴百科 2、用手左右滑动顶部banner图片 3、发现图片切换顺序有手势顺序相反 4、且是白点不是绿点：", "fid":7}' \
   http://[base-api]/feedback
```


## 获取用户反馈 GET /feedback

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| fid | 主题ID | 否，不提供时返回所有反馈 |

返回结果按时间倒序排列。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X GET \
   http://[base-api]/feedback
```


```json
{
  "code": 200,
  "data": [
    {
      "content": "1、进入育婴百科 2、用手左右滑动顶部banner图片 3、发现图片切换顺序有手势顺序相反 4、且是白点不是绿点：",
      "ua": "curl/7.52.1",
      "ts": 1500021585999,
      "replies": [],
      "status": "open",
      "nts": 0,
      "appid": "3Dk7UesISm6SyhjyfiocwA",
      "id": 4859,
      "tag": "无类别",
      "aid": 161705
    }
  ]
}
```


# 打卡


## 上传资源（文章图片、App背景） PUT /resource

静态资源以请求body的形式传入。成功时返回资源的md5值,和图片尺寸（宽高）。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -H 'Content-Type: application/binary' \
 -X PUT \
 --data-binary "@/home/garfield/downloads/50_avatar_middle.jpg" \
  http://[base-api]/resource
```

返回资源URI地址：

```json
{"code":200,"data":"654350ea6f443e5db4d9fabc77e96a70","size":"320,240"}
```

下载时使用返回的`data`值拼接URL如`http://[base-api]/icon/654350ea6f443e5db4d9fabc77e96a70`。见上文[下载资源(头像、打卡图片、App背景) GET /icon/:path]


## 阿里OSS上传静态资源

参考 [文档](https://help.aliyun.com/document_detail/32011.html?spm=a2c4g.11186623.2.13.2625c06d6QHMsH#concept-32011-zh)

以Java为例使用如下配置上传文件：

```java
String endpoint = "https://oss-cn-beijing.aliyuncs.com";
String accessKeyId = "<yourAccessKeyId>";
String accessKeySecret = "<yourAccessKeySecret>";
String bucketName = "jianqing";
```

上传时，先计算待上传文件的md5值，转换为小写后，作为objectName值上传

```java
ossClient.putObject(bucketName, objectName, filein);
```

上传成功后，下载路径是

```java
https://jianqing.oss-cn-beijing.aliyuncs.com/<objectName>
```

## 添加文章或回复 PUT /mblog

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| parent_id | 文章ID | 存在时为回复，不存在时为打卡 |
| ~~pic~~ | ~~图片uri~~ | ~~否，老版本app使用~~ |
| pics | 多张图片uri以逗号拼接 | 否，新版本app多图打卡使用 |
| ~~pic_size~~ | ~~图片尺寸~~ | ~~否~~ |
| msg | 文字内容 | 否 |
| hidden | 是否为私有， `y,n`之一, `y`为私有 | 否，默认为`n` |
| act_id | 活动id | 否 |
| tag | 活动名 | 否 |

* 只有`tag`无`act_id`时，为用户自己创建的活动。
* 有`act_id`时，为用户参加管理员创建的活动。


```bash
# 单图
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X PUT \
 -d '{"pic":"654350ea6f443e5db4d9fabc77e96a70", "msg":"断网了", "hidden":"n", "act_id": 1, "tag": "ksak"}' \
   http://[base-api]/mblog
   
# 多图
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X PUT \
 -d '{"pics":"3693e68f46423d33c305a8d55f58d6b4,5b891f08f6a8e1f85fcc5e45acf6e848", "msg":"多图了", "hidden":"n", "tag": "2图"}' \
   http://[base-api]/mblog
```

成功后返回mblog_id及`sqn`（打卡号）

```json
{"code":200,"data":{"mblog_id":4,"sqn":4}}
```

## 删除文章或回复 DELETE /mblog

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 文章ID | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X DELETE \
 -d '{"id":"4"}' \
   http://[base-api]/mblog
```


## 更新文章或回复 POST /mblog

只提供须更新的字段。不能允许完全删除图片或文字。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 文章ID | 是 |
| pic | 图片uri | 否 |
| msg | 文字内容 | 否 |
| hidden | 是否为私有， `y,n`之一 | 否，默认为`n` |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X POST \
 -d '{"id":"3", "msg":"中新网2月21日电中央气象台2月21日06时继续发布暴雪黄色预警：预计，21日08时至22日08时，陕西东南部、山西中南部、河北南部、河南大部、湖北西北部、山东大部、辽宁东部等地有大雪，其中河南中北部、山东西南部、湖北西北部、陕西东南部的部分地区有暴雪(10～18毫米)，上述地区新增积雪深度3～10厘米，局地可达12厘米以上。"}' \
   http://[base-api]/mblog
```


## 获取指定账号收到的评论 GET /received/replies

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| account_id | 账号id | 否，不提供时为当前用户 |
| lastid | 上次的文章id | 否，默认从最新一条开始 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X GET \
    http://[base-api]/received/replies
```

```json
{
  "code": 200,
  "data": [
    {
      "mblog_msg": "断网了",
      "mblog_pic": "654350ea6f443e5db4d9fabc77e96a70",
      "account_id": 16,
      "msg": "🌸蛇🐍是",
      "account": {
        "account_id": 16,
        "nickname": "Heefy",
        "sex": "男",
        "icon_image_path": "9c026523c7ddc3f77cf9178b1b7a9120"
      },
      "ts": 1488258070391,
      "mblog_id": 3,
      "hidden": "n",
      "parent_id": 3,
      "id": 28,
      "pic": null,
      "sqn": 0
    },
    {
      "mblog_msg": "断网了",
      "mblog_pic": "654350ea6f443e5db4d9fabc77e96a70",
      "account_id": 16,
      "msg": "噶v给猪🐷",
      "account": {
        "account_id": 16,
        "nickname": "Heefy",
        "sex": "男",
        "icon_image_path": "9c026523c7ddc3f77cf9178b1b7a9120"
      },
      "ts": 1488257779694,
      "mblog_id": 7,
      "hidden": "n",
      "parent_id": 7,
      "id": 25,
      "pic": null,
      "sqn": 0
    }
  ]
}
```

## 获取指定账号发表的文章或评论 GET /mblog

返回按id倒序排列，`id=lastid`之前的20条记录

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 文章id | 否 |
| account_id | account id | 否，不提供时为当前用户 |
| lastid | 上次的文章id | 否，默认从最新一条开始 |
| parent_id | 文章ID | 存在时返回单一文章的回复 |
| reply_only | `y`时只返回回复 | 否，不提供时只返回打卡 |

> `parent_id`与`reply_only`不能同时使用

> 返回主题时可同时返回回复列表与点赞用户列表。

**说明**

1. 获取指定用户发表的文章，使用`account_id`及`lastid`参数
1. 获取指定用户发表的回复，使用`account_id`及`reply_only=y`参数
1. 获取指定用户对某一篇文章的回复，使用`account_id`及`parent_id`


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X GET \
   'http://[base-api]/mblog?reply_only=n'
```

```json
{
  "code": 200,
  "data": [
    {
      "account": {
        "account_id": 566,
        "afav_cnt": 0,
        "follower_cnt": 0,
        "following_cnt": 2,
        "icon_image_path": "15ab9838a7083b9ebd548b7510909c2d",
        "nickname": "alien",
        "sex": "男",
        "signature": "summer star citizen"
      },
      "account_id": 566,
      "act_id": 51,
      "followed": "n",
      "hidden": "n",
      "id": 18541,
      "ispop": 0,
      "like": "n",
      "msg": "第一天",
      "parent_id": null,
      "pic": null,
      "pic_size": null,
      "pics": null,
      "pop_weight": 0,
      "sqn": 10,
      "tag": "跑步打卡营",
      "ts": 1558483585819,
      "weight": 0
    },
    {
      "account": {
        "account_id": 566,
        "afav_cnt": 0,
        "follower_cnt": 0,
        "following_cnt": 2,
        "icon_image_path": "15ab9838a7083b9ebd548b7510909c2d",
        "nickname": "alien",
        "sex": "男",
        "signature": "summer star citizen"
      },
      "account_id": 566,
      "act_id": null,
      "followed": "n",
      "hidden": "n",
      "id": 12063,
      "ispop": 0,
      "like": "n",
      "msg": "冬天儿歌",
      "parent_id": null,
      "pic": "3c3ba38f2e9ba4759fe1e053f2f20402",
      "pic_size": "2000,1412",
      "pics": "3c3ba38f2e9ba4759fe1e053f2f20402",
      "pop_weight": 0,
      "sqn": 9,
      "tag": null,
      "ts": 1546650997932,
      "weight": 0
    }
  ],
  "msg": "OK"
}
```

## 获取某一文章的所有回复 GET /mblog/replies

返回文章的所有回复，结果按id顺序排列。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| parent_id | 文章id | 是 |

```bash
curl -k -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
  -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X GET \
  'http://[base-api]/mblog/replies?parent_id=42'
```

返回如下：

```json
{
  "code": 200,
  "data": [
    {
      "id": 43,
      "parent_id": 42,
      "account_id": 16,
      "pic": null,
      "ts": 1488425039283,
      "hidden": "n",
      "sqn": 0,
      "msg": "GG刚发的"
    },
    {
      "id": 44,
      "parent_id": 42,
      "account_id": 16,
      "pic": null,
      "ts": 1488426082709,
      "hidden": "n",
      "sqn": 0,
      "msg": "👄花_(:з」∠)_😏"
    }
  ]
}
```


## 批量获取公开的用户信息 GET /public/account

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| aids | 逗号分割的account_id | 是 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
  -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X GET \
  http://[base-api]/public/account?aids=1,2,7,999
```

返回账户第一角色信息，及`follower_cnt， following_cnt， afav_cnt`（粉丝数、关注数、收藏数）。

```json
{
  "code": 200,
  "data": [
    {
      "account_id": 7,
      "nickname": "testokok U穿78",
      "sex": "女",
      "icon_image_path": "0951908ffabc204a82d16388d1fdd877",
      "follower_cnt": 0,
      "following_cnt": 0,
      "afav_cnt": 0
    },
    {
      "account_id": 1,
      "nickname": "郭争永",
      "sex": "男",
      "icon_image_path": "11436960942.png",
      "follower_cnt": 0,
      "following_cnt": 0,
      "afav_cnt": 0
    },
    {
      "account_id": 999,
      "nickname": "Yoyo",
      "sex": "女",
      "icon_image_path": "9991451918438.png",
      "follower_cnt": 0,
      "following_cnt": 0,
      "afav_cnt": 0
    },
    {
      "account_id": 2,
      "nickname": "你好",
      "sex": "男",
      "icon_image_path": " ",
      "follower_cnt": 0,
      "following_cnt": 0,
      "afav_cnt": 0
    }
  ]
}
```

## 获取动态列表 GET /mblog/moments

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| page | 指定页(>=1),按权重倒序排列 | 否 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 3cc4b355f3deaa02da8da8d831795bcf6fea979c2b544c2396be3440664c2b68 ' \
 -X GET \
  http://[base-api]/mblog/moments?page=1
```

返回如下，其中`liked_by`表示点赞人列表；`like`表示当前用户是否点赞；`replies`为回复列表；`followed`表示是否关注。

```json

```

## 关注 PUT /follow/:account_id

`account_id`为被关注的人的id


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X PUT  \
   http://[base-api]/follow/6
```

## 取消关注 DELETE /follow/:account_id

`account_id`为被关注的人的id

## 获取我的粉丝 GET /followers


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| aid | 账号ID | 否，默认为当前用户 |
| page | 页数，从1开始 | 否 |
| cnt | 每页条目数 | 否 |




```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X GET  \
   http://[base-api]/followers
```

返回如下，

* 其中`mutual`表示是(y)否(n)互相关注。
* `follow_status`表示所列账号（X）与当前登录的账号（I）的关注关系，含义如下

| follow_status值 | 描述 |
| ------------- | :------------- |
| 1 | X与I无关注关系 |
| 2 | X关注了I |
| 3 | I关注了X | |
| 4 | I与X互相关注 |
| 5 | I与X是同一个账号 |



```json
{
  "code": 200,
  "data": [
    {
      "sex": "女",
      "account_id": 50,
      "signature": "",
      "nickname": "Cishi2",
      "icon_image_path": " ",
      "following_cnt": 5,
      "follower_cnt": 3,
      "ts": 1488448390883,
      "mutual": "n",
      "afav_cnt": 1
    },
    {
      "sex": "女",
      "account_id": 34243,
      "signature": null,
      "nickname": "QQ用来测试哒主号",
      "icon_image_path": "5d061f64bfc2f342e4f97679b101d5a6",
      "following_cnt": 15,
      "follower_cnt": 4,
      "ts": 1489109603643,
      "mutual": "n",
      "afav_cnt": 3
    }
  ]
}
```


## 获取我的关注 GET /following


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| aid | 账号ID | 否，默认为当前用户 |
| page | 页数，从1开始 | 否 |
| cnt | 每页条目数 | 否 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498 ' \
 -X GET  \
   http://[base-api]/following?aid=161770
```

返回如下，其中`mutual`表示是(y)否(n)互相关注，`follow_status`参考[#获取我的粉丝](#获取我的粉丝_get_/followers)。

```json
{
  "code": 200,
  "msg": "OK",
  "data": [
    {
      "sex": "女",
      "account_id": 161778,
      "signature": "走的路多了，可能就瘦了",
      "nickname": "指纹在旅行",
      "follow_status": 4,
      "icon_image_path": "c0d8f84ae7d4af74c19cb782211ee2a5",
      "following_cnt": 3,
      "follower_cnt": 7,
      "ts": 1505382399856,
      "mutual": "y",
      "afav_cnt": 0
    },
    {
      "sex": "女",
      "account_id": 161772,
      "signature": "肉肉我们分手吧，我不爱你了",
      "nickname": "yuki",
      "follow_status": 1,
      "icon_image_path": "02051cde1b04e0e29b698356fd472c3f",
      "following_cnt": 3,
      "follower_cnt": 2,
      "ts": 1508920294217,
      "mutual": "y",
      "afav_cnt": 0
    }
  ]
}
```

## 获取我关注的人的文章 GET /mblog/following

返回按id倒序排列的文章列表。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| lastid | 上页最小id值 | 否，默认从最新一条开始 |
| cnt | 每页条目数 | 否，默认20 |


```bash
curl -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 30b97b608023a52f2c6616b2bba4f2cf8ff71edb57f8486380e6e9714f92fb70 ' \
 -X GET  \
   'http://[base-api]/mblog/following?cnt=10'
```

返回数据同[获取达人文章 GET /pop/mblog]

## 获取相关活动的文章 GET /mblog/byactid

返回按id倒序排列的文章列表。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| actid | 活动id | 是 |
| lastid | 上页最小id值 | 否，默认从最新一条开始 |
| cnt | 每页条目数 | 否，默认20 |



```bash
curl -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
  -X GET  \
   'http://[base-api]/mblog/byactid?cnt=10&actid=1'
```

返回数据同[获取达人文章 GET /pop/mblog]

```json
{
  "code": 200,
  "data": [
    {
      "account": {
        "account_id": 195263,
        "afav_cnt": 2,
        "follower_cnt": 11,
        "following_cnt": 5,
        "icon_image_path": "20775cb49d71e4a7eecfc582704541e1",
        "nickname": "滚滚猫",
        "sex": "女",
        "signature": "加油加油"
      },
      "account_id": 195263,
      "act_id": 37,
      "followed": "n",
      "hidden": "n",
      "id": 12836,
      "ispop": 0,
      "like": "n",
      "liked_by": [
        {
          "account": {
            "account_id": 229281,
            "afav_cnt": 0,
            "follower_cnt": 0,
            "following_cnt": 0,
            "icon_image_path": "",
            "nickname": "丁丁",
            "sex": "女",
            "signature": ""
          },
          "account_id": 229281,
          "ts": 1552713517365
        }
      ],
      "msg": null,
      "parent_id": null,
      "pic": "e714811b03573fbcb9bb37e3d34c2297",
      "pic_size": "500,375",
      "pics": "e714811b03573fbcb9bb37e3d34c2297",
      "pop_weight": 0,
      "sqn": 15,n
      "tag": "低卡餐食谱",
      "ts": 1548127055913,
      "weight": 0
    },
    {
      "account": {
        "account_id": 217410,
        "afav_cnt": 0,
        "follower_cnt": 3,
        "following_cnt": 1,
        "icon_image_path": "6f3c3bde59dfec8e6e0269d6f2847798",
        "nickname": "Merry",
        "sex": "女",
        "signature": "爱家的女人"
      },
      "account_id": 217410,
      "act_id": 37,
      "followed": "n",
      "hidden": "n",
      "id": 12808,
      "ispop": 0,
      "like": "n",
      "msg": null,
      "parent_id": null,
      "pic": "5ad884957acfb5b5257c9105d03219ba",
      "pic_size": "913,1166",
      "pics": "5ad884957acfb5b5257c9105d03219ba",
      "pop_weight": 0,
      "sqn": 1,
      "tag": "913,1166",
      "ts": 1548121876489,
      "weight": 0
    }
  ],
  "msg": "OK"
}
```


## 获取推荐关注 GET /recommend/users

返回所有推荐用户，服务器有1分钟缓存。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X GET  \
   http://[base-api]/recommend/users
```


```json
{
  "code": 200,
  "data": [
      {
      "account_id": 84535,
      "nickname": "꼬꼬댁+",
      "sex": "男"
    },
    {
      "account_id": 84418,
      "nickname": "天意",
      "sex": "男"
    }
  ]
}
```

## 点赞/取消点赞 GET /mblog/like/toggle/:blog_id

`:blog_id`为目标文章的ID


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X GET \
    http://[base-api]/mblog/like/toggle/7
```

## 获取收到的点赞 GET /received/likes

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| aid | 账号ID | 否，默认为当前用户 |
| mblog_id | 主题ID | 否，用于查询某一主题下的点赞 |
| lastid | 上页的id最小值 | 否，默认从最新一条开始 |
| cnt | 条数 | 否 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 6fabcddaswwdkwwkks19eb8f2dd5485e46eb7423ca2a88a61bd204f0f ' \
 -X GET \
    http://[base-api]/received/likes?cnt=10
```

返回如下

```json
{
  "code": 200,
  "data": [
    {
      "liked_by": {
        "account_id": 566,
        "nickname": "alien",
        "sex": "男"
      },
      "like_ts": 1487830999007,
      "account_id": 566,
      "msg": "断网了",
      "ts": 1487830950424,
      "hidden": "n",
      "parent_id": null,
      "id": 7,
      "pic": "654350ea6f443e5db4d9fabc77e96a70",
      "sqn": 7
    }
  ]
}
```




## 获取达人文章 GET /pop/mblog

返回按权重排列的达人文章。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| cnt | 条数 | 否，默认10 |
| page | 页数 | 否，默认1 |

返回条目数小于`cnt`时表示已经到末页，没有更多数据。

```bash
curl -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' 'http://[base-api]/pop/mblog?cnt=4&page=3'
```


返回如下

```json
{
  "code": 200,
  "data": [
    {
      "liked_by": [
        {
          "account_id": 160756,
          "ts": 1499905936557,
          "account": {
            "account_id": 160756,
            "nickname": "木易羊羊",
            "sex": "男",
            "icon_image_path": "bebe62229d7181ca7bf4e27bc9700506",
            "signature": "",
            "follower_cnt": 2,
            "following_cnt": 2,
            "afav_cnt": 0
          }
        }
      ],
      "act_id": 0,
      "account_id": 162165,
      "pop_weight": 10,
      "msg": "今天继续减重，吃水果、喝水",
      "ispop": 1,
      "account": {
        "account_id": 162165,
        "nickname": "卷毛",
        "sex": "女",
        "icon_image_path": "90da364f0bfc149099e142b34d888a91",
        "signature": "保持体重，增加骨量",
        "follower_cnt": 8,
        "following_cnt": 0,
        "afav_cnt": 0
      },
      "ts": 1499903451200,
      "weight": 8,
      "hidden": "n",
      "parent_id": null,
      "id": 5535,
      "pic": "a24c7c16d739d808f4495f33d65777f5",
      "tag": null,
      "sqn": 11
    },
    {
      "act_id": 0,
      "account_id": 171,
      "pop_weight": 4,
      "msg": "打卡打卡…………",
      "ispop": 1,
      "account": {
        "account_id": 171,
        "nickname": "毛毛",
        "sex": "女",
        "icon_image_path": "d74e468766a329980d3e3461168dfca0",
        "signature": "",
        "follower_cnt": 3,
        "following_cnt": 1,
        "afav_cnt": 0
      },
      "ts": 1499961373378,
      "weight": 0,
      "hidden": "n",
      "parent_id": null,
      "id": 5604,
      "pic": "1a9d9407009c6f69d43596d3b42f3b45",
      "tag": null,
      "sqn": 6
    },
    {
      "liked_by": [
        {
          "account_id": 92043,
          "ts": 1499915113895,
          "account": {
            "account_id": 92043,
            "nickname": "自由行走的花",
            "sex": "男",
            "icon_image_path": "29dab82241d6324196eaf359ae395705",
            "signature": "好好增肥",
            "follower_cnt": 45,
            "following_cnt": 639,
            "afav_cnt": 0
          }
        },
        {
          "account_id": 160756,
          "ts": 1499905978751,
          "account": {
            "account_id": 160756,
            "nickname": "木易羊羊",
            "sex": "男",
            "icon_image_path": "bebe62229d7181ca7bf4e27bc9700506",
            "signature": "",
            "follower_cnt": 2,
            "following_cnt": 2,
            "afav_cnt": 0
          }
        }
      ],
      "act_id": 0,
      "account_id": 140847,
      "pop_weight": 3,
      "msg": "还差8.6公斤，加油👊",
      "ispop": 1,
      "account": {
        "account_id": 140847,
        "nickname": "~惔萣丶",
        "sex": "女",
        "icon_image_path": "e6cf28429677ef47136221953a078ffa",
        "signature": "",
        "follower_cnt": 4,
        "following_cnt": 2,
        "afav_cnt": 0
      },
      "ts": 1499902937042,
      "weight": 102,
      "hidden": "n",
      "parent_id": null,
      "id": 5532,
      "pic": "c706de2141ad63a7e2cd347c722d640f",
      "tag": null,
      "sqn": 3
    },
    {
      "act_id": 0,
      "account_id": 170799,
      "pop_weight": 3,
      "msg": "加油↖(^ω^)↗",
      "ispop": 1,
      "account": {
        "account_id": 170799,
        "nickname": "紫雨",
        "sex": "女",
        "icon_image_path": "5654a7fff166915086aecf4c8148d28b",
        "signature": "",
        "follower_cnt": 0,
        "following_cnt": 0,
        "afav_cnt": 0
      },
      "ts": 1499958581193,
      "weight": 0,
      "hidden": "n",
      "parent_id": null,
      "id": 5598,
      "pic": "a2156ec5b1214c751a2777d8bfbe5cea",
      "tag": null,
      "sqn": 1
    }
  ]
}
```

## 获取Banner GET /banner

```bash
curl -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'  http://[base-api]/banner
```

返回如下

```json
{
  "code": 200,
  "data": [
    {
      "id": 2,
      "pic": "/logo/e330f373e3a9e1c55e4a07c66f25900e.jpg",
      "url": "http://www.baidu.com",
      "pos": 2,
      "content": "月旺评吧",
      "title": "月旺评",
      "ts": 1499679554512
    },
    {
      "id": 3,
      "pic": "/logo/53a7af0984b880f5f25a4263f96c87d8.jpg",
      "url": "https://www.baidu.com/home/news/data/newspage?nid=3163904076100566013&n_type=0&p_from=1&dtype=-1",
      "pos": 3,
      "content": "随时可行",
      "title": "去看海",
      "ts": 1499679604574
    }
  ]
}
```

## 获取活动列表 GET /activity

```bash
curl -H 'cs-app-id:ebcad75de0d42a844d98a755644e30'  http://[base-api]/activity
```

状态(`status`)值为`pending, online, offline`。

返回如下

```json
{
  "code": 200,
  "data": [
    {
      "description": "洞晓",
      "ts_end": 1500480000000,
      "slogan": "吃qq了",
      "picb": "/logo/f4ffcbd13c8fd8ecf46f9832a3729602.jpg",
      "howtojoin": "碰运气",
      "pica": "/logo/f5b8acbb03e14d520a88929f3f70b59a.jpg",
      "reward": "马龙三交响曲",
      "title": "比吃",
      "ts": 1499757832078,
      "status": "pending",
      "id": 1,
      "mcount": 0,
      "position": 2,
      "ts_start": 1499757190787
    }
  ]
}
```


## 举报 PUT /mblog/report

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| mid | 文章或回复的ID | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 5e1bf58ff9be2d742a80c1aa1e193fcf682629eb40094bc3b88358f2681d3f33 ' \
 -d '
 {"mid": 2547, "type": "post"}
 ' \
 -X PUT \
    http://[base-api]/mblog/report
```

返回


```json
{"code":200}
```






# 商城

说明

* 一个商品对应一个物流信息。
* 一个订单可含有一种或多种商品。
* 订单生成后，保留30分钟。这30分钟内，商家修改价格不影响该订单价格
* 如果不需要多App同步购物车，那么购物车可在App本地存取，不用调服务器接口



## 获取卖家信息 GET /seller/:id

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 店铺id  | 是 |

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://[base-api]/seller/1'
```

返回如下

```json
{
  "code": 200,
  "data": {
    "description": "ah",
    "id": 1,
    "owner": "haier",
    "phone": "1214",
    "title": "haier"
  },
  "msg": "OK"
}
```




## 获取商品 GET /product


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| page | 页码，从1开始  | 否，默认为1 |

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://[base-api]/product?page=1'
```

返回如下

```json
{
  "code": 200,
  "data": [
    {
      "carousel": null,
      "cover": "a0799a38a2eaa3b668f63885ad73eaf5.jpg",
      "description": "超轻碳素羽毛球拍2支装正品碳纤维复合双拍耐打成人初学单拍学生\n超轻碳素 质保一年 打断包赔",
      "id": 35,
      "images": "a0799a38a2eaa3b668f63885ad73eaf5.jpg",
      "pid": 35,
      "price": 4000,
      "score": 5000,
      "seller": {
        "description": "无",
        "id": 9,
        "owner": "王振山",
        "phone": "13964091591",
        "title": "海尔泰山远创专卖店"
      },
      "sid": 9,
      "status": "show",
      "tag": "运动,健身,减肥",
      "thumbnail": null,
      "title": "羽毛球拍",
      "ts": 1545199544146
    } 
  ],
  "msg": "OK"
}
```

返回字段说明：

| 名称 | 描述 |
| ------------- | :------------- |
| id | 商品ID  |
| title | 商品名  | |
| description | 商品描述 |
| images | 以英文逗号（,）分开的商品详情图片 |
| cover | 以英文逗号（,）分开的商品封面图片 |
| carousel | 以英文逗号（,）分开的轮播图图片 |
| thumbnail | 以英文逗号（,）分开的列表小图图片 |
| status | `show`:销售中，`hide`:已下架 |
| price | 商品价格，单位为分 |
| score | 购买所需积分 |
| tag | 以英文逗号（,）分开标签文字 |
| sid | 卖家ID |

## 按标题搜索商品 GET /shop/product/q

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| term | 查询条件 | 是 |
| page | 页码，从1开始  | 否，默认为1 |

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X GET \
  'http://[base-api]/shop/product/q?term=df'
```

返回数据结构与[获取商品 GET /product](获取商品_GET_/product)相同。

## 添加用户地址 PUT /address

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| fullname | 收货人姓名 | 是 |
| phone | 收货人手机号 | 是 |
| province | 省，直辖市 | 是 |
| city | 市 | 否 |
| area | 区 | 否 |
| address | 完整地址 | 是 |
| zipcode | 邮编 | 是，若不需要可传`000000` |
| isdefault | 是否设为默认，`y`(是）或`n`（否） | 是 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"fullname": "张三", "phone": "18822223333", "province": "广东", "city": "深圳", "address": "井冈山2282号", "zipcode": "000000", "isdefault": "y"}
 ' \
 -X PUT \
    http://[base-api]/address
```

添加成功后返回如下，其中`id`为该地址的ID，

```json
{
  "code": 200,
  "msg": "OK",
  "data": {
    "fullname": "张三",
    "phone": "18822223333",
    "province": "广东",
    "city": "广州",
    "address": "井冈山2282号",
    "zipcode": "000000",
    "isdefault": "y",
    "aid": 195966,
    "id": 4
  }
}
```
## 修改用户地址 POST /address

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 数据id | 是 |
| fullname | 收货人姓名 | 是 |
| phone | 收货人手机号 | 是 |
| province | 省，直辖市 | 是 |
| city | 市 | 否 |
| area | 区 | 否 |
| address | 完整地址 | 是 |
| zipcode | 邮编 | 是，若不需要可传`000000` |
| isdefault | 是否设为默认，`y`(是）或`n`（否） | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"id": 4, "fullname": "李四", "phone": "18822223333", "province": "广东", "city": "深圳", "address": "井冈山2282号", "zipcode": "000000", "isdefault": "y"}
 ' \
 -X POST \
    http://[base-api]/address
```



## 删除用户地址 DELETE /address

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| id | 数据id | 是 |

```bash
curl -k  -v \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -H 'Content-Type: application/json' \
 -H "Accept-Encoding: gzip" \
 -X DELETE \
  'http://[base-api]/address?id=1'
```

## 获取用户的地址 GET /address

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -X GET \
    http://[base-api]/address
```


返回如下，各字段含义见`PUT /address`接口说明，

``` json
{
  "code": 200,
  "msg": "OK",
  "data": [
    {
      "address": "井冈山2282号",
      "phone": "18822223333",
      "city": "深圳",
      "isdefault": "n",
      "fullname": "张三",
      "zipcode": "000000",
      "ts": 1541237891366,
      "id": 3,
      "area": null,
      "aid": 195966,
      "province": "广东"
    },
    {
      "address": "井冈山2282号",
      "phone": "18822223333",
      "city": "深圳",
      "isdefault": "y",
      "fullname": "李四",
      "zipcode": "000000",
      "ts": 1541237945894,
      "id": 4,
      "area": null,
      "aid": 195966,
      "province": "广东"
    }
  ]
}
```

## 生成订单 PUT /order

根据传入的商品列表及地址信息生成订单。

> 如果商品需要积分，会检查用户是否有足够的积分，如果积分不足则返回`code: 409`。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| products | 商品列表数组，数据如下 | 是 |
| address | 地址 | 是 |
| payment_type | 支付方式，`weixin`（当前不支持）或`alipay`（当前不支持）, `yl-weixin` (银联微信, 当前不支持), `yl-alipay` （银联支付宝）, `unionpay` (银联云闪付),  `kjt-h5`(快捷通H5) | 否，不提供时不返回预支付信息 |


`products`数组各元素字段：

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| pid | 商品id | 是 |
| quantity | 数据 | 是 |

`address`传用户使用的地址，其字段与上面的地址信息相同：

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| fullname | 收货人姓名 | 是 |
| phone | 收货人手机号 | 是 |
| province | 省，直辖市 | 是 |
| city | 市 | 否 |
| area | 区 | 否 |
| address | 完整地址 | 是 |
| zipcode | 邮编 | 是，若不需要可传`000000` |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"address": {"fullname": "张三", "phone": "18822223333", "province": "广东", "city": "深圳", "address": "井冈山2282号", "zipcode": "000000"},
  "products": [{"pid": 4, "quantity": 2}]}
 ' \
 -X PUT \
    http://[base-api]/order
```

订单生成成功后，返回订单详情，包含地址及商品快照，(请求时包含`payment_type`还会返回预支付信息`prepay`)。一级目录的`id`用户删除订单，`ouid`用于界面展示为订单号。

**传入暂不支持的支付类型时，`prepay`返回`null`。**


```json
{
  "code": 200,
  "data": {
    "address": "井冈山2282号",
    "aid": 195966,
    "city": "深圳",
    "fullname": "张三",
    "id": 318,
    "ouid": "20190305095302153",
    "payment_type": "kjt-h5",
    "phone": "18822223333",
    "prepay": {
      "prepay_id": "https://zcash-h5.kjtpay.com/loading?cashierType=H5&token=750e6806aa994f469056bc1a5962b294&partnerId=200001725745"
    },
    "price": 10,
    "products": [
      {
        "aid": 195966,
        "carousel": "e2cb85a36ce14a57b823dc564b5d4721.jpg",
        "cover": "66f4e02ca24cf60786802b823a6664fc.jpg",
        "description": "1.0",
        "images": "3dea5268b03ae43b77f47a89821c6cb6.jpg",
        "oid": 318,
        "pid": 64,
        "price": 10,
        "quantity": 1,
        "score": 0,
        "seller": {
          "description": "无",
          "id": 9,
          "owner": "王振山",
          "phone": "13964091591",
          "title": "海尔泰山远创专卖店"
        },
        "sid": 9,
        "tag": "",
        "thumbnail": "3dea5268b03ae43b77f47a89821c6cb6.jpg",
        "title": "1.0"
      }
    ],
    "province": "广东",
    "score": 0,
    "status": "pending",
    "ts": 1551750782153,
    "zipcode": "000000"
  },
  "msg": "OK"
}
```

`payment_type`为`kjt-h5`时返回示例，其中`prepay`为H5页面链接。

``` json
{
  "code": 200,
  "data": {
    "address": "井冈山2282号",
    "aid": 195966,
    "city": "深圳",
    "fullname": "张三",
    "id": 141,
    "ouid": "20190226115006159",
    "payment_type": "kjt-h5",
    "phone": "18822223333",
    "prepay": "https://zcash-h5.kjtpay.com/loading?cashierType=H5&token=253adc4f705b402392faf2bf5eab4a08&partnerId=200001725745",
    "price": 10,
    "products": [
      {
        "aid": 195966,
        "carousel": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "cover": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "description": "塔式",
        "images": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "oid": 141,
        "pid": 22,
        "price": 10,
        "quantity": 1,
        "score": 1,
        "seller": {
          "description": "haier",
          "id": 1,
          "owner": "haier",
          "phone": "123456",
          "title": "haier"
        },
        "sid": 1,
        "tag": "堪",
        "thumbnail": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "title": "1分1角"
      }
    ],
    "province": "广东",
    "score": 1,
    "status": "pending",
    "ts": 1551153006176,
    "zipcode": "000000"
  },
  "msg": "OK"
}
```

`payment_type`为`yl-alipay`时返回如下:

```json
{
  "code": 200,
  "data": {
    "address": "井冈山2282号",
    "aid": 195966,
    "city": "深圳",
    "fullname": "张三",
    "id": 142,
    "ouid": "20190328080244985",
    "payment_type": "yl-alipay",
    "phone": "18822223333",
    "prepay": {
      "prepay_id": "{\"qrCode\":\"https://qr.alipay.com/bax08800gfpfftrx10fk00f0\"}"
    },
    "price": 10,
    "products": [
      {
        "aid": 195966,
        "carousel": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "cover": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "description": "塔式",
        "images": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "oid": 142,
        "pid": 22,
        "price": 10,
        "quantity": 1,
        "score": 1,
        "seller": {
          "description": "haier",
          "id": 1,
          "owner": "haier",
          "phone": "123456",
          "title": "haier"
        },
        "sid": 1,
        "tag": "堪",
        "thumbnail": "6f79416e09b1b63c64c2c9e6c0860b1a.png",
        "title": "1分1角"
      }
    ],
    "province": "广东",
    "score": 1,
    "status": "pending",
    "ts": 1553731364986,
    "zipcode": "000000"
  },
  "msg": "OK"
}
```


## 确认订单 PUT /order/confirm

> 对于用户已支付订单，后台管理填写订单的物流信息后，默认N天自动确认收货。用户也可以从客户端手动提前确认收货。

* 当前订单状态必须为已支付`paid`才能调用此接口确认收货。
* 请求成功后，订单状态修改为`complete`。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| oid | 订单id | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 3d6199a1c94d949e1736f0a8424323a177f9419a3b2b4c289635e525edf2bff5 ' \
 -d '{"oid": 9}' \
 -X PUT \
    http://localhost:3333/order/confirm
```




## 微信/支付宝预下单 /PUT /shop/prepay


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| payment_type | `weixin`或`alipay`, `kjt-h5` | 是 |
| oid | 订单ID | 是 |


```bash
curl -k \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'Content-Type: application/json' \
-H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
-d '
{"payment_type":"weixin", "oid": 5}
' \
-X PUT \
'http://[base-api]/shop/prepay'

```


返回如下：

```json
{
  "code": 200,
  "data": {
    "id": 318,
    "payment_type": "kjt-h5",
    "prepay": {
      "prepay_id": "https://zcash-h5.kjtpay.com/loading?cashierType=H5&token=4676f1b435e34112bac8486f8671ba26&partnerId=200001725745"
    }
  },
  "msg": "OK"
}
```


## 获取订单 GET /order
    
批量获取订单列表。


| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| page | 页码 | 否，默认1，每页20条 |
| cnt | 每页数量 | 否，默认20条 |
| ouid | 订单号 | 否，传入时，将忽略`page`及`cnt`参数，传入时对于银联订单会更新订单状态 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
  -X GET \
    http://[base-api]/order
```

返回`data`字段说明 ：

| 字段 | 描述  | 类型 |
| ------------- | :------------- | ----- |
| aid | 用户id | number |
| address | 见[添加用户地址 PUT /address](#添加用户地址%20PUT%20/address) | string |
| area | 见[添加用户地址 PUT /address](#添加用户地址%20PUT%20/address) | string |
| city | 见[添加用户地址 PUT /address](#添加用户地址%20PUT%20/address) | string |
| fullname | 见[添加用户地址 PUT /address](#添加用户地址%20PUT%20/address) | string |
| id | 订单id | number |
| ouid | 订单编号 | string |
| phone | 见[添加用户地址 PUT /address](#添加用户地址%20PUT%20/address) | string |
| price | 订单总金额，单位分 | number |
| products | 订单商品列表，见下文 | array |
| province | 见[添加用户地址 PUT /address](#添加用户地址%20PUT%20/address) | string |
| score | 订单总积分消耗 | number |
| status | 见下文 | string |

订单状态`status`的含义如下

| `status`值 | 描述 |
| ------------- | :------------- |
| pending | 待支付  |
| delivery | 已发货  |
| paid | 已支付，用户未确认  | |
| complete | 已完成，用户已确认收货 |
| cancel | 已取消 |
| hide | 已删除 |


商品列表`products`数组各字段含义可参考[获取商品 GET /product](#获取商品%20GET%20/product)及下表：

| 字段 | 描述  | 类型 |
| ------------- | :------------- | ----- |
| aid | 用户id | number |
| oid | 订单id | number |
| quantity | 该商品的购买数量 | number |
| seller | 卖家基本信息 | object |
| sid | 卖家id | number |
| ship_provider | 快递提供商 | string |
| ship_id | 快递号 | string |
| returns | 本商品的退换货列表，见下表 | array |

商品退换货列表`returns`数组各字段：

| 字段 | 描述  | 类型 |
| ------------- | :------------- | ----- |
| quantity | 退换数量 | number |
| status | pending: 未处理; processing: 退款中; success: 成功; reject: 已拒绝 | string |
| tpe | return: 退货；exchange：换货 | string |




```json
{
  "code": 200,
  "data": [
    {
      "address": "井冈山2282号",
      "aid": 195966,
      "area": null,
      "city": "深圳",
      "fullname": "张三",
      "id": 131,
      "ouid": "20181211192711252",
      "phone": "18822223333",
      "price": 0,
      "products": [
        {
          "aid": 195966,
          "carousel": null,
          "cover": "7e51d8294d5cb1d2bdb48d23bea201c8.png",
          "description": "最好用的称心如意",
          "id": 112,
          "images": "2586aaf70c2785db8fb6e46b58f5b575.jpg,401f2c2007c7cfce1c444d11ab43d0de.jpg",
          "oid": 131,
          "pid": 1,
          "price": 1,
          "quantity": 2,
          "returns": [
            {
              "quantity": 1,
              "status": "pending",
              "tpe": "return"
            },
            {
              "quantity": 1,
              "status": "pending",
              "tpe": "exchange"
            }
          ],
          "score": 2,
          "seller": {
            "id": 9,
            "owner": "baidu",
            "phone": "18575522830",
            "title": "boring"
          },
          "ship_id": "dafdf",
          "ship_provider": "fdasf",
          "sid": 9,
          "tag": "",
          "thumbnail": null,
          "title": "WIFI 2000"
        }
      ],
      "province": "广东",
      "score": 4,
      "status": "complete",
      "ts": 1544527631252,
      "zipcode": "000000"
    }
  ],
  "msg": "OK"
}
```



## 查询订单 GET /order

查询单个订单并获取最新支付状态。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| ouid | 订单号 | 是 |


返回同`获取订单 GET /order`接口。

## 删除订单 DELETE /order

不允许删除已支付但尚未确认的订单。


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
  -X DELETE \
    'http://[base-api]/order?oid=3'
```




## 获取购物车 GET /cart

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
  -X GET \
    'http://[base-api]/cart'
```

返回购物车商品及数量，其中`price`为单价，`quantity`为该商品数量。


```json
{
  "code": 200,
  "msg": "OK",
  "data": [
    {
      "description": "最好用的WIFI智能秤",
      "images": "159591bcecf9e879730fb95e145ff5d4.jpg,6cb28fb64df7e17a36836abe8e858e8f.jpg,1b8e3377d9857f3382451880bd50ba44.jpg",
      "cover": "3573fb66be0507fd9fe26110bc48af96.jpg,6cb28fb64df7e17a36836abe8e858e8f.jpg,a6480dbcf3c40d66f3d5c99aef2fbf78.jpg",
      "title": "WIFI 200",
      "ts": 1541236441300,
      "status": "show",
      "id": 3,
      "score": 20,
      "tag": "积分促销,11.11",
      "quantity": 1,
      "price": 9980,
      "sid": 1
    },
    {
      "description": "最好用的智能秤",
      "images": "5aeb566164b1bd9229ae649001da186a.jpg",
      "cover": "a6480dbcf3c40d66f3d5c99aef2fbf78.jpg,a2c63e43903624daa43cf9e32a777eba.jpg",
      "title": "BUSK",
      "ts": 1541236628009,
      "status": "show",
      "id": 4,
      "score": 10,
      "tag": "10积分",
      "quantity": 2,
      "price": 38880,
      "sid": 1
    }
  ]
}
```


## 修改购物车商品数量，添加或删除商品 POST /cart/product

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| pid | 商品id | 是 |
| quantity | 数量，删除时传0 | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"pid": 3, "quantity": 1}
 ' \
 -X POST \
    http://[base-api]/cart/product
```

## 购物车商品数量加一 POST /cart/product/increment

> 7.添加购物车需要新增接口: 传商品id 该商品数量自动加1

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| pid | 商品id | 是 |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"pid": 1}
 ' \
 -X POST \
    http://[base-api]/cart/product/increment
```




## 批量删除商品 DELETE /cart/product

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| pid | 商品id用逗号（`,`）拼接 | 是 |


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"pid": "3,4"}
 ' \
 -X DELETE \
    http://[base-api]/cart/product
```



## 清空购物车 DELETE /cart

删除购物车的所有商品。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
  -X DELETE \
    'http://[base-api]/cart'
```



## 下载商品详情图/封面图 GET /broadcast/images/:path

拼接链接如下下载：

```
http://45.35.13.168:8080/broadcast/images/c30b468b888a64932b8e26f8a05afa90.jpg
```


## 获取置顶商品 GET /shop/top/product


```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: cca02a7fa48f1826964948da5f72ab44ee53d2029633437b8988c33271fd726c' \
  -X GET \
    'http://45.35.13.168:8080/shop/top/product'
```

返回如下。按`loc`从小到大的顺序展示。

```json
{
  "code": 200,
  "data": [
    {
      "cover": "527b48d695ab1f190d550a013f65ce0c.jpg",
      "description": "123456",
      "id": 2,
      "id_2": 12,
      "images": "527b48d695ab1f190d550a013f65ce0c.jpg",
      "loc": 2,
      "pid": 12,
      "price": 1200,
      "score": 1,
      "seller": {
        "description": "haier",
        "id": 1,
        "owner": "haier",
        "phone": "123456",
        "title": "haier"
      },
      "sid": 1,
      "status": "show",
      "tag": "",
      "title": "体脂称",
      "ts": 1541494036682,
      "ts_2": 1541494036660
    } 
  ],
  "msg": "OK"
}
```

## 退货 POST /shop/order/product/return

退货提交成功后，须由管理员确认后，才会退还相应商品的金额与积分。

以下的`pid, ouid`必须由原订单获取。

| 参数名称 | 描述 | 是否必须 |
| ------------- | :------------- | ----- |
| pid | 商品id | 是 |
| ouid | 原订单OUID | 是 |
| quantity | 商品数量 | 是，必须大于0小于原订单中商品数量 |
| ship_provider | 物流提供商 | 是 |
| ship_id | 物流号，寄回商品的物流号 | 是 |
| reason | 退货原因 | N |

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"pid": 1, "ouid": "20181211192711252", "quantity":1, "ship_provider": "顺丰", "ship_id": "9982211210174203515"}
 ' \
 -X POST \
    http://[base-api]/shop/order/product/return
```

## 换货 POST /shop/order/product/exchange

参数同退货。换货不退积分不退款。

```bash
curl -k \
 -H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
 -H 'Content-Type: application/json' \
 -H 'cs-token: 8777742756711311636490623598956559740857577060728149792783599498' \
 -d '
 {"pid": 1, "ouid": "20181211192711252", "quantity":1, "ship_provider": "顺丰", "ship_id": "9982211210174203515"}
 ' \
 -X POST \
    http://[base-api]/shop/order/product/exchange
```


## 仅退款 POST /shop/order/product/refund

参数同退货。退款不用填写订单信息。

# 按年龄推送

客户端初始化jpush时，须设置tag，参考[jpush文档](http://docs.jiguang.cn/jpush/client/Android/android_api/#api_3)

约定只考虑主角色的年龄。

格式如`age_KEY`，其中`KEY`取值如下。例如年龄为22岁的用户，注册tag的格式为`ag_b`

```text
"a" "小于18"
"b" "18~25"
"c" "26~30"
"d" "31~40"
"e" "41~50"
"f" "51~60"
"g" "60以上"
```

最后用到的tag如下

```text
{"tag":["age_c","age_d"],"tag_and":["sex_2","companyid_15"]}
```

# 返回码列表

```clojure
{:exception                    {:code 1 :msg "系统异常!"}
   :session-expired              {:code 2 :msg "未登录"}
   :auth-failed                  {:code 3 :msg "无效访问"}
   :params-conflict              {:code 4 :msg "参数有冲突！"}
   :invalid-access-token         {:code 5 :msg "Openid对应的access_token无效"}
   :api-not-implemented          {:code 6 :msg "此接口尚未开放"}
   :success                      {:code 200 :msg "OK"}
   :account_len_invalid          {:code 101 :msg "帐户名长度超过其允许的最大长度(注册)"}
   :account_no_exist             {:code 102 :msg "用户帐号不存在(登录)"}
   :register_account_exist       {:code 103 :msg "该帐户已经注册(注册) "}
   :register_failure             {:code 104 :msg "注册失败(注册)"}
   :account_password_error       {:code 105 :msg "密码错误(登录)"}
   :account_not_loggin           {:code 106 :msg "用户没有登录，服务器拒绝执行该请求(其他)"}
   ;; :email_send_failure           {:code 107 :msg   "邮件发送失败(用户反馈)"}
   :app_id_verify_error          {:code 108 :msg "app_id不正确"}
   :role_add_error               {:code 109 :msg "角色添加失败"}
   :role_already_exist           {:code 110 :msg "角色已经存在"}
   :role-not-exist               {:code 111 :msg "原角色不存在"}
   :role-already-deleted         {:code 112 :msg "角色已经删除"}
   :weigher_already_exist        {:code 113 :msg "设备已经存在"}
   :weigher_already_error        {:code 114 :msg "设备添加失败"}
   :weigher_del_error            {:code 115 :msg "添加删除失败"}
   :weigher_update_error         {:code 116 :msg "修改设备失败"}
   :role_find_error              {:code 117 :msg "用户查询失败"}
   :software_version_newest      {:code 118 :msg "软件已经是最新版本"}
   :role_feedback_err            {:code 119 :msg "暂时没有数据"}
   :image_create_err             {:code 120 :msg "图片生成失败"}
   :image_db_err                 {:code 121 :msg "数据库操作失败"}
   :role_feedback_invalid        {:code 122 :msg "用户反馈意见长度超过其允许的最大长度(用户反馈)"}
   :main-role-delete-not-allowed {:code 123 :msg "不能删除主账号"}
   :unbind-not-allowed           {:code 131 :msg "不能解绑主账号"}
   :already-bind                 {:code 132 :msg "已经绑定"}
   :vericode-invalid             {:code 133 :msg "验证码错误"}
   :verifycode_send_error        {:code 134 :msg "验证码发送失败"}
   :too-many-roles               {:code 135 :msg "家庭成员最多不能超过8人"}
   :required-param-not-exist     {:code 301 :msg "缺少必要参数"}
   :invalid-uid                  {:code 302 :msg "uid格式不合法"}
   :invalid-provider             {:code 303 :msg "登录类型不明，必须是sina_blog/qq/uid之一"}
   :invalid-phone                {:code 304 :msg "手机号格式不合法"}
   :invalid-param                {:code 305 :msg "参数不合法"}
   :request-interval-too-short   {:code 306 :msg "请求太频繁!"}
   :id-taken                     {:code 307 :msg "当前账号已被使用"}
   :pwds-equal                   {:code 308 :msg "新旧密码相同"}
   :invalid-sex                  {:code 309 :msg "性别不合法：男/女"}
   :unknown-action               {:code 310 :msg "未知操作"}
   :invalid-reminder-time        {:code 311 :msg "提醒时间无效"}
   :invalid-length-unit          {:code 312 :msg "长度单位无效"}
   :invalid-weight-unit          {:code 313 :msg "质量单位无效"}
   :invalid-company-id           {:code 314 :msg "companyid无效"}
   
   :openid-taken                {:code 315 :msg "当前QQ或新浪账号已经在使用"}
   :invalid-email               {:code 316 :msg "email格式不合法"}
   :invalid-mtype               {:code 400 :msg "测量类别无效"}
   :emtpy-comment-content       {:code 401 :msg "评论内容为空"}
   :invalid-ptype               {:code 402 :msg "统计时段无效"}
   :account-disabled            {:code 403 :msg "账号已被禁用"}
   :account-post-mblog-disabled {:code 404 :msg "账号已被禁言"}
   :order-not-exists            {:code 405 :msg "订单不存在"}
   :order-invalid-state         {:code 406 :msg "订单正在处理中，不允许修改"}
   :product-removed             {:code 407 :msg "商品已下架"}
   :invalid-payment-type        {:code 408 :msg "支付方式无效"}
   :not-enough-score            {:code 409 :msg "积分不足"}}
```


# 阿里OSS
## STS服务接口 ANY /oss/sts

由OSS SDK调用，见[文档](https://help.aliyun.com/document_detail/31920.html?spm=a2c4g.11186623.2.12.411a2d71m4iK2s#concept-kxc-brw-5db)




# U+ App接口

## 登录

同[haier登录 POST /account/login](#haier登录%20POST%20/account/login)

登录成功后，从响应的`header`中取出`cs-token`，并用于后继的用户身份认证与识别。

## Q81 WIFI秤数据解析 POST /wifi/weight/parse

| 参数名称 | 描述 | 是否必须 | 类型 |
| ------------- | :------------- | :--------: |
| `rid`  | 海尔U家角色ID | 是，若不区分传入0 | string |
| `weight`  | 体重，单位kg | 是 | number |
| `ts`  | 称重时间，UNIX时间戳，毫秒值  | 是 | number |
| age  | 年龄，正整数 | 否 | number |
| birthday  | 生日格式`yyyy-MM-dd` | 否 | string |
| sex  | 性别, 男：1；0：女 | 是 | number |
| height  | 身高，单位cm | 是 | number |
| r1  | 电阻值 | 是 | number |
| rn8  | 8电极电阻值，格式为`1:f1,f2,f3,f4,f5`。 `f1——f5`为浮点数阻值 | 否 | string |
| save  | 是否保存至服务器，`y`：是；其它值否。 | 否 | string |

* 同一账号同一角色相同时间(即`rid`与`ts`都相同)的称重数据自动覆盖。
* 不提供`age`参数时，使用`birthday`计算年龄。两个参数必须提供至少一个

请求如下：

```bash
curl  \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'Content-Type: application/json' \
-H 'cs-token: [cs-token] ' \
-d '
{"rid": "0", "weight": 62.5, "ts": 1554860279706, "birthday": "1998-10-23", "sex":0, "height": 155, "r1": 423.22, "rn8": "1:651.5,474.5,477.2,507.2,509.9", "save": "y"}
' \
-X POST \
'http://[base-uri]/wifi/weight/parse'
```

响应如下，其中`readable`为解析出的数据，

```json
{
  "code": 200,
  "data": {
    "age": 35,
    "aid": 195966,
    "height": 188,
    "r1": 523.22,
    "readable": {
      "bfr": {
        "level": 1,
        "percent": 5.0,
        "tip": "标准",
        "value": 5.0
      },
      "birthday": "1983-09-09",
      "bmi": {
        "level": 1,
        "percent": 25.746944427490238,
        "tip": "偏瘦",
        "value": 12.873472
      },
      "bmr": {
        "level": 2,
        "percent": 43.70333251953125,
        "tip": "正常",
        "value": 1311.1
      },
      "fm": {
        "level": 1,
        "percent": 5.0,
        "tip": "标准",
        "value": 2.275
      },
      "hint": "你的理想体重是70.80公斤，现在的你太瘦弱了，建议每天3餐，保证必要的营养摄入，并针对自身体质特征进行调养，增强身体的消化吸收力，充分吸收食物的营养，让多余的能量转化为肌肉和脂肪，以此来得到体重的增长。",
      "msw": {
        "level": 1,
        "percent": 20.0,
        "tip": "偏低",
        "value": 1.0
      },
      "od": {
        "level": -1.0,
        "percent": 18.181818181818183,
        "tip": "消瘦",
        "value": -35.734467
      },
      "score": {
        "value": 61.0
      },
      "slm": {
        "level": 3,
        "percent": 92.79,
        "tip": "优",
        "value": 42.22
      },
      "slm_percent": {
        "level": 3,
        "percent": 92.79,
        "tip": "优",
        "value": 92.79
      },
      "tf": {
        "level": 1,
        "percent": 69.61,
        "tip": "不足",
        "value": 31.672552
      },
      "tfr": {
        "level": 1,
        "percent": 69.61,
        "tip": "不足",
        "value": 69.61
      },
      "weight_change": 5.0
    },
    "rid": "0",
    "rn8": "1:651.5,474.5,477.2,507.2,509.9",
    "sex": 0,
    "ts": 1553960279706,
    "weight": 45.5
  },
  "msg": "OK"
}
```

### 解析字段说明

```
bfr: 脂肪率
bm: 标准肌肉
bmi: BMI
bmr: 基础代谢率
bodyage: 身体年龄
bw: 标准体重
fc: 脂肪控制
fm: 脂肪重
hl: 臀围
labfr: 左手脂肪率
laslm: 左手肌肉量
lbm: 瘦体重
llbfr: 左脚脂肪率
llslm: 左脚肌肉量
mc: 肌肉控制
msw: 骨量
od: 肥胖度
pm: 蛋白质
rabfr: 右手脂肪率
raslm: 右手肌肉量
rlbfr: 右脚脂肪率
rlslm: 右脚肌肉量
score: 身体得分
shape: 获取体型 -1:消瘦 0:普通 1:隐形肥胖 2:肌肉型肥胖 3:肥胖
slm: 肌肉重
tf: 总水重
tfr: 水分百分比
trbfr: 躯干脂肪率
vfr: 内脏脂肪等级
wc: 体重控制
wl: 腰围
```

## Q81 WIFI秤数据查询 GET /wifi/weight

按时间倒序获取称重数据

| 参数名称 | 描述 | 是否必须 | 类型 |
| ------------- | :------------- | :--------: |
| `rid`  | 海尔U家角色ID | 否，若不传返回所有角色的数据 | string |
| lastts  | 时间戳`ts`。传入时查询此时间之前的数据，不传入时从最新一条数据开始查询 | 否 | number |
| cnt  | 每页数据条目数，默认20 | 否 | number |


请求示例：

```bash
curl  \
-H 'cs-app-id:ebcad75de0d42a844d98a755644e30' \
-H 'cs-token: [cs-token] ' \
-X GET \
'http://[base-api]/wifi/weight?cnt=1'
```

返回如下：

```json
{
  "code": 200,
  "data": {
    "age": 35,
    "aid": 195966,
    "height": 188,
    "r1": 523.22,
    "readable": {
      "bfr": {
        "level": 1,
        "percent": 5.0,
        "tip": "标准",
        "value": 5.0
      },
      "birthday": "1983-09-09",
      "bmi": {
        "level": 1,
        "percent": 25.746944427490238,
        "tip": "偏瘦",
        "value": 12.873472
      },
      "bmr": {
        "level": 2,
        "percent": 43.70333251953125,
        "tip": "正常",
        "value": 1311.1
      },
      "fm": {
        "level": 1,
        "percent": 5.0,
        "tip": "标准",
        "value": 2.275
      },
      "hint": "你的理想体重是70.80公斤，现在的你太瘦弱了，建议每天3餐，保证必要的营养摄入，并针对自身体质特征进行调养，增强身体的消化吸收力，充分吸收食物的营养，让多余的能量转化为肌肉和脂肪，以此来得到体重的增长。",
      "msw": {
        "level": 1,
        "percent": 20.0,
        "tip": "偏低",
        "value": 1.0
      },
      "od": {
        "level": -1.0,
        "percent": 18.181818181818183,
        "tip": "消瘦",
        "value": -35.734467
      },
      "score": {
        "value": 61.0
      },
      "slm": {
        "level": 3,
        "percent": 92.79,
        "tip": "优",
        "value": 42.22
      },
      "slm_percent": {
        "level": 3,
        "percent": 92.79,
        "tip": "优",
        "value": 92.79
      },
      "tf": {
        "level": 1,
        "percent": 69.61,
        "tip": "不足",
        "value": 31.672552
      },
      "tfr": {
        "level": 1,
        "percent": 69.61,
        "tip": "不足",
        "value": 69.61
      },
      "vfr": {
        "level": 2,
        "percent": 2.0,
        "tip": "标准",
        "value": 1.0
      },
      "weight_change": 5.0
    },
    "rid": "0",
    "rn8": "1:651.5,474.5,477.2,507.2,509.9",
    "sex": 0,
    "ts": 1553960279706,
    "weight": 45.5
  },
  "msg": "OK"
}
```

单位

```
score: 无
weight_change: kg
weight: kg
od: %
bmr: 千卡/日
msw: kg
slm: kg
slm_percent: %
bfr: %
fm: kg
tfr: %
tf: kg
bmi: kg/m^2
vfr: 无
```

