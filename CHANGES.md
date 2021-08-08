<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [0.1.32](#0132)
- [0.1.22](#0122)
  - [升级说明](#%E5%8D%87%E7%BA%A7%E8%AF%B4%E6%98%8E)
  - [变更](#%E5%8F%98%E6%9B%B4)
    - [已知问题](#%E5%B7%B2%E7%9F%A5%E9%97%AE%E9%A2%98)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# 0.1.32
* 新增RPC接口
* 新增HTTP讲坛接口
* 生成随机用户
* 升级逻辑修改
* nginx/wordpress访问配置


# 0.1.22

## 升级说明
* 升级前需先执行合并国际版数据库脚本：


```sql
--- !!!执行前备份数据库!!!
source 006-merge-intl-okok.sql
call import_intl_data();
```

* 配置文件中添加参数`:sms-2`（普天短信）及`:pull-intl-okok-data-period`(定时导入PHP国际版数据)


## 变更
* 合并国际版数据库：导入用户及测量数据。
* company_id为2时，使用putian短信接口。
* `/latestapp`接口变更，见接口文档。

### 已知问题
* 使用PHP国际版接口更新、删除的数据不会并入okok数据库中。
