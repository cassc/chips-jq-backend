(ns chips.codes)


(def cds-map
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
   
   :openid-taken                    {:code 315 :msg "当前QQ或新浪账号已经在使用"}
   :invalid-email                   {:code 316 :msg "email格式不合法"}
   :invalid-mtype                   {:code 400 :msg "测量类别无效"}
   :emtpy-comment-content           {:code 401 :msg "评论内容为空"}
   :invalid-ptype                   {:code 402 :msg "统计时段无效"}
   :account-disabled                {:code 403 :msg "账号已被禁用"}
   :account-post-mblog-disabled     {:code 404 :msg "账号已被禁言"}
   :order-not-exists                {:code 405 :msg "订单不存在"}
   :order-invalid-state             {:code 406 :msg "订单正在处理中，不允许修改"}
   :product-removed                 {:code 407 :msg "商品已下架"}
   :invalid-payment-type            {:code 408 :msg "支付方式无效"}
   :not-enough-score                {:code 409 :msg "积分不足"}
   :order-status-invalid            {:code 410 :msg "订单状态无效"}
   :order-no-need-to-pay            {:code 411 :msg "订单无须支付"}
   :order-from-same-seller-required {:code 412 :msg "不同店铺的商品请分别下单！"}
   :coupon-exists                   {:code 413 :msg "兑换码无效或已使用！"}
   :product-not-exists              {:code 414 :msg "商品不存在！"}
   :not-enough-prod-to-return       {:code 415 :msg "退换商品数量超过实际购买数量！"}
   :invalid-order-status-to-return  {:code 416 :msg "订单完成后方可退换商品！"}
   :order-same-prod-should-group    {:code 417 :msg "同一订单相同商品不能分开！"}
   :invalid-order-status            {:code 418 :msg "当前订单状态下不允许此操作！"}
   })


(def ^:prviate err (RuntimeException. "Code name not exists!"))

(defn cds
  "Returns `(:code-name cds)` or throws an exception if the `:code-name`
  does not exist in `cds`"
  ([code-name]
   (if-let [c (get cds-map code-name)]
     c
     (throw err)))
  ([code-name alt-map]
   (merge (cds code-name) alt-map)))

(comment
  (run! (fn [{:keys [code msg]}]
          (println (format "| %d | %s |" code msg)))
        (sort-by :code (vals cds-map))))
