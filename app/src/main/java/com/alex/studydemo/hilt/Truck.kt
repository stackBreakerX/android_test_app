//package com.alex.studydemo.hilt
//
//import com.alex.studydemo.hilt.hilt_interface.Engine
//import com.alex.studydemo.hilt.hitl_annotation.BindElectricEngine
//import com.alex.studydemo.hilt.hitl_annotation.BindGasEngine
//import javax.inject.Inject
//
///**
// * @description
// * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
// * @time 2021/1/20 4:11 下午
// * @version
// */
//class Truck @Inject constructor(val driver: Driver) {
//
//    @Inject
//    @BindGasEngine
//    lateinit var gasEngine: Engine
//
//    @Inject
//    @BindElectricEngine
//    lateinit var electricEngine: Engine
//
//    fun deliver() {
//        gasEngine.start()
//        electricEngine.start()
//        println("Truck is delivering cargo.")
//        gasEngine.shutdown()
//        electricEngine.shutdown()
//    }
//
//    fun truckName() = "Alex"
//
//}