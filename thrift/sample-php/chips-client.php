<?php

error_reporting(E_ALL);

require_once '/usr/lib/php5/lib/Thrift/ClassLoader/ThriftClassLoader.php';

use Thrift\ClassLoader\ThriftClassLoader;

$GEN_DIR = realpath(dirname(__FILE__).'/..').'/gen-php';

$loader = new ThriftClassLoader();
$loader->registerNamespace('Thrift', '/usr/lib/php5/lib');
$loader->registerDefinition('chips', $GEN_DIR);
$loader->register();

use Thrift\Protocol\TBinaryProtocol;
use Thrift\Transport\TSocket;
use Thrift\Transport\THttpClient;
use Thrift\Transport\TBufferedTransport;
use Thrift\Exception\TException;

use chips\Request;

function getClient(){
    $socket = new TSocket('192.168.0.72', 9110);
    $socket->setSendTimeout(60000);
    $socket->setRecvTimeout(60000);

    $transport = new TBufferedTransport($socket, 1024, 1024);
    $protocol = new TBinaryProtocol($transport);
    $client = new \chips\UserServiceClient($protocol);
    $transport->open();
    return $client;
}

function ping(){
    $client = getClient();
    echo $client->ping();
}

// 注册与找回密码前获取的手机验证码
function testVericode(){
    $client = getClient();
    $resp = $client->vericode(array("phone"=>"18575522826", "flag"=>"0"));
    echo ("vericode Response: ".$resp->code." ".$resp->msg."\n");

}


// 注册
function testRegister(){
    try {
        $client = getClient();
        $user =array("phone"=>"18575522826", "password"=>"123456", "vericode"=>"7370");
        $resp =  $client->registerUser($user);
        echo ("registerUser response: ".$resp->code." ".$resp->msg."\n");
    } catch (TException $tx) {
        echo ('TException: '.$tx->getMessage()."\n");
    }
}

// 获取用户资料
// 动态结构将以json字符串的形式返回到`json`字段
function testUserInfo(){
    $client = getClient();
    $resp = $client->userInfo(array("phone"=>"18575522826"));
    print_r($resp);
}

// 更改密码
function testPwdUpdate(){
    $client = getClient();
    $resp = $client->updatePassword(array("phone"=>"18680391418", "oldpwd"=>"", "newpwd"=>"adas"));
    print_r($resp);
}

// 添加角色
// 注意文件的传入方法
function testAddRole(){
    $client = getClient();
    $icon = file_get_contents("/home/garfield/images/3-2.jpg");
    $req = new Request(array("content"=>array("phone"=>"18575522826", "nickname"=>"boring", "height"=>"177", "birthday"=>"1987-09-09", "sex"=>"男"), "file"=>$icon));
    $resp = $client->addRole($req);
    print_r($resp);
}

// 更新角色 w/头像
function testUpdateRole(){
    $client = getClient();
    $icon = file_get_contents("/home/garfield/images/3-2.jpg");

    $req = new Request(array("content"=>array("phone"=>"18575522826", "oldnickname"=>"sea", "nickname"=>"seal"), "file"=>$icon));
    $resp = $client->updateRole($req);
    print_r($resp);
}

ping();
testVericode();
testRegister();
testUpdateRole();
testAddRole();
testUserInfo();
testPwdUpdate();



?>