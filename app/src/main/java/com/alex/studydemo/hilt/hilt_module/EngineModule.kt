//package com.alex.studydemo.hilt.hilt_module
//
//import com.alex.studydemo.hilt.ElectricEngine
//import com.alex.studydemo.hilt.GasEngine
//import com.alex.studydemo.hilt.hilt_interface.Engine
//import com.alex.studydemo.hilt.hitl_annotation.BindElectricEngine
//import com.alex.studydemo.hilt.hitl_annotation.BindGasEngine
//import dagger.Binds
//import dagger.Module
//import dagger.hilt.InstallIn
//import dagger.hilt.android.components.ActivityComponent
//
///**
// * @description
// * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
// * @time 2021/1/20 5:49 下午
// * @version
// */
//@Module
//@InstallIn(ActivityComponent::class)
//abstract class EngineModule {
//
//    @Binds
//    @BindGasEngine
//    abstract fun bindGasEngine(gasEngine: GasEngine):Engine
//
//    @Binds
//    @BindElectricEngine
//    abstract fun bindElectricEngine(electricEngine: ElectricEngine):Engine
//}