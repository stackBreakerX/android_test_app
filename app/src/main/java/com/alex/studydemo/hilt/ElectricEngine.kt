package com.alex.studydemo.hilt

import com.alex.studydemo.hilt.hilt_interface.Engine

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/1/20 5:39 下午
 * @version
 */
class ElectricEngine  constructor() : Engine {
    override fun start() {
        println("Electric engine start.")
    }

    override fun shutdown() {
        println("Electric engine shutdown.")
    }
}