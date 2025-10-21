package com.alex.studydemo.hilt

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/1/20 4:08 下午
 * @version
 */
//@AndroidEntryPoint
//class HiltMainActivity : AppCompatActivity() {
//
//    private lateinit var viewBinding: ActivityHiltMainBinding
//
//    private val viewModel: HiltViewModel by lazy { ViewModelProvider(this).get(HiltViewModel::class.java) }
//
//    @Inject
//    lateinit var truck: Truck
//
//    companion object {
//        fun newInstance(context: Context) {
//            val intent = Intent(context, HiltMainActivity::class.java)
//            context.startActivity(intent)
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        viewBinding = ActivityHiltMainBinding.inflate(layoutInflater)
//        setContentView(viewBinding.root)
//        truck.deliver()
//        viewBinding.textView2.text = truck.truckName()
//        Handler(mainLooper).postDelayed({
//            viewBinding.textView2.text = truck.driver.driverName()
//        }, 2000)
//
//        Handler(mainLooper).postDelayed({
//            viewBinding.textView2.text = viewModel.getName()
//        }, 2000)
//
//        viewBinding.textView3.text = viewModel.getOtherName()
//    }
//
//}