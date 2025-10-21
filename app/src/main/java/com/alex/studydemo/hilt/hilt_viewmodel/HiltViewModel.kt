//package com.alex.studydemo.hilt.hilt_viewmodel
//
//import android.app.Application
//import androidx.hilt.lifecycle.ViewModelInject
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.ViewModel
//import com.alex.studydemo.hilt.hilt_repository.MyRepository
//import com.alex.studydemo.hilt.hilt_repository.OtherRepository
//
///**
// * @description
// * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
// * @time 2021/1/20 6:30 下午
// * @version
// */
//class HiltViewModel @ViewModelInject constructor(
//    private val myRepository: MyRepository,
//    private val otherRepository: OtherRepository
//) : ViewModel() {
//
//    fun getName() = myRepository.getName()
//
//    fun getOtherName() = otherRepository.getOtherName()
//}