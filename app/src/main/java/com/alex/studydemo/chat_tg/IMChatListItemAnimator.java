//package com.alex.studydemo.chat_tg;
//
//import android.animation.Animator;
//import android.animation.AnimatorListenerAdapter;
//import android.animation.AnimatorSet;
//import android.animation.ObjectAnimator;
//import android.animation.ValueAnimator;
//import android.util.LongSparseArray;
//import android.view.View;
//import android.view.ViewPropertyAnimator;
//import android.view.animation.Interpolator;
//import android.view.animation.OvershootInterpolator;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.core.view.ViewCompat;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//
//public class IMChatListItemAnimator1 extends DefaultItemAnimator {
//
//    // 匹配 Telegram 聊天消息的时间曲线，用于添加/移动/移除/进入过渡动画
//    public static final long DEFAULT_DURATION = 250;
//    // 默认插值器
//    public static final Interpolator DEFAULT_INTERPOLATOR = new CubicBezierInterpolator(0.19919472913616398, 0.010644531250000006, 0.27920937042459737, 0.91025390625);
//
//    @Nullable
//    // 聊天活动引用
//    private final TgTextChatActivity activity;
//    // 列表视图引用
//    private final RecyclerView recyclerListView;
//
//    // 将要移除的分组消息
////    private HashMap<Integer, MessageObject.GroupedMessages> willRemovedGroup = new HashMap<>();
//    // 将要改变的分组消息
////    private ArrayList<MessageObject.GroupedMessages> willChangedGroups = new ArrayList<>();
//
//    // 正在进行的动画映射
//    HashMap<RecyclerView.ViewHolder,Animator> animators = new HashMap<>();
//    // 灭霸效果涉及的视图列表
//    ArrayList<View> thanosViews = new ArrayList<>();
//
//    // 动画结束时运行的任务列表
//    ArrayList<Runnable> runOnAnimationsEnd = new ArrayList<>();
//    // 分组 ID 到进入延迟的映射
//    HashMap<Long, Long> groupIdToEnterDelay = new HashMap<>();
//
//    // 是否应该从底部动画进入
//    private boolean shouldAnimateEnterFromBottom;
//    // 问候贴纸的 ViewHolder
//    private RecyclerView.ViewHolder greetingsSticker;
//    // 聊天问候视图
////    private ChatGreetingsView chatGreetingsView;
//
//    // 是否反转位置
//    private boolean reversePositions;
//    // 资源提供者
//    private final Theme.ResourcesProvider resourcesProvider;
//
//    // 构造函数
//    public IMChatListItemAnimator1(TgTextChatActivity activity, RecyclerView listView, Theme.ResourcesProvider resourcesProvider) {
//        // 设置资源提供者
//        this.resourcesProvider = resourcesProvider;
//        // 设置聊天活动
//        this.activity = activity;
//        // 设置列表视图
//        this.recyclerListView = listView;
//        // 设置平移插值器
//        translationInterpolator = DEFAULT_INTERPOLATOR;
//        // 设置总是创建移动动画
//        alwaysCreateMoveAnimationIfPossible = true;
//        // 设置不支持改变动画（因为我们自己处理）
//        setSupportsChangeAnimations(false);
//    }
//
//    @Override
//    // 运行挂起的动画
//    public void runPendingAnimations() {
//        // 检查是否有挂起的移除
//        boolean removalsPending = !mPendingRemovals.isEmpty();
//        // 检查是否有挂起的移动
//        boolean movesPending = !mPendingMoves.isEmpty();
//        // 检查是否有挂起的改变
//        boolean changesPending = !mPendingChanges.isEmpty();
//        // 检查是否有挂起的添加
//        boolean additionsPending = !mPendingAdditions.isEmpty();
//        // 如果没有任何挂起的动画，直接返回
//        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
//            return;
//        }
//
//        // 决定下一次插入是否应该从底部飞入（类似聊天的进入效果）或原地淡入
//        boolean runTranslationFromBottom = false;
//        // 如果应该从底部动画进入
//        if (shouldAnimateEnterFromBottom) {
//            // 遍历挂起的添加列表
//            for (int i = 0; i < mPendingAdditions.size(); i++) {
//                // 如果位置反转（通常是聊天界面）
//                if (reversePositions) {
//                    // 获取当前项总数
//                    int itemCount = recyclerListView.getAdapter() == null ? 0 : recyclerListView.getAdapter().getItemCount();
//                    // 如果是最后一项
//                    if (mPendingAdditions.get(i).getLayoutPosition() == itemCount - 1) {
//                        // 标记为从底部进入
//                        runTranslationFromBottom = true;
//                    }
//                } else {
//                    // 如果不是反转位置，且是第一项
//                    if (mPendingAdditions.get(i).getLayoutPosition() == 0) {
//                        // 标记为从底部进入
//                        runTranslationFromBottom = true;
//                    }
//                }
//            }
//        }
//
//        // 动画开始回调
//        onAnimationStart();
//
//        // 如果从底部进入
//        if (runTranslationFromBottom) {
//            // 使用自定义的进入过渡，将输入框链接到消息气泡
//            runMessageEnterTransition();
//        } else {
//            // 对于非聊天插入，使用通用的 alpha/平移过渡
//            runAlphaEnterTransition();
//        }
//
//        // 创建一个 ValueAnimator 用于触发更新
//        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
//        // 添加更新监听器
//        valueAnimator.addUpdateListener(animation -> {
//            // 在动画期间通知宿主 ChatActivity 进行失效重绘
//            if (activity != null) {
//                // 调用 Activity 的 tick 方法
//                activity.onListItemAnimatorTick();
//            } else {
//                // 否则直接 invalidate 列表视图
//                recyclerListView.invalidate();
//            }
//        });
//        // 设置持续时间为移除持续时间 + 移动持续时间
//        valueAnimator.setDuration(getRemoveDuration() + getMoveDuration());
//        // 开始动画
//        valueAnimator.start();
//    }
//
//    // Alpha 进入延迟
//    long alphaEnterDelay;
//
//    // 运行 Alpha 进入过渡
//    private void runAlphaEnterTransition() {
//        // 检查是否有挂起的移除
//        boolean removalsPending = !mPendingRemovals.isEmpty();
//        // 检查是否有挂起的移动
//        boolean movesPending = !mPendingMoves.isEmpty();
//        // 检查是否有挂起的改变
//        boolean changesPending = !mPendingChanges.isEmpty();
//        // 检查是否有挂起的添加
//        boolean additionsPending = !mPendingAdditions.isEmpty();
//        // 如果没有任何挂起的动画
//        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
//            // 没有什么可动画的
//            return;
//        }
//        // 首先，移除项目
//        // 标记是否有灭霸效果
//        boolean hadThanos = false;
//        // 检查是否支持灭霸效果
//        final boolean supportsThanos = getThanosEffectContainer != null && supportsThanosEffectContainer != null && supportsThanosEffectContainer.run();
//        // 如果支持灭霸效果
//        if (supportsThanos) {
//            // 用于存储需要用灭霸效果移除的分组
//            LongSparseArray<ArrayList<RecyclerView.ViewHolder>> groupsToRemoveWithThanos = null;
//            // 遍历挂起的移除列表
//            for (int i = 0; i < mPendingRemovals.size(); ++i) {
//                // 获取 ViewHolder
//                RecyclerView.ViewHolder holder = mPendingRemovals.get(i);
//                // 如果在待响指列表中，且是消息单元格，且有分组信息
//                if (toBeSnapped.contains(holder) && holder.itemView instanceof ChatMessageCell && ((ChatMessageCell) holder.itemView).getCurrentMessagesGroup() != null) {
//                    // 获取消息对象
//                    MessageObject msg = ((ChatMessageCell) holder.itemView).getMessageObject();
//                    // 如果消息不为空且有分组 ID
//                    if (msg != null && msg.getGroupId() != 0) {
//                        // 初始化分组移除映射
//                        if (groupsToRemoveWithThanos == null) {
//                            groupsToRemoveWithThanos = new LongSparseArray<>();
//                        }
//                        // 获取该分组的 ViewHolder 列表
//                        ArrayList<RecyclerView.ViewHolder> holders = groupsToRemoveWithThanos.get(msg.getGroupId());
//                        // 如果列表为空，创建新列表
//                        if (holders == null) {
//                            groupsToRemoveWithThanos.put(msg.getGroupId(), holders = new ArrayList<>());
//                        }
//                        // 从待响指列表中移除
//                        toBeSnapped.remove(holder);
//                        // 从挂起移除列表中移除
//                        mPendingRemovals.remove(i);
//                        // 索引回退
//                        i--;
//                        // 添加到该分组的列表中
//                        holders.add(holder);
//                    }
//                }
//            }
//            // 如果有需要用灭霸效果移除的分组
//            if (groupsToRemoveWithThanos != null) {
//                // 遍历所有分组
//                for (int i = 0; i < groupsToRemoveWithThanos.size(); ++i) {
//                    // 检查是否移除整个分组
//                    ArrayList<RecyclerView.ViewHolder> holders = groupsToRemoveWithThanos.valueAt(i);
//                    // 如果列表为空，继续
//                    if (holders.size() <= 0) continue;
//                    // 标记是否整个分组
//                    boolean wholeGroup = true;
//                    // 获取第一个 ViewHolder
//                    RecyclerView.ViewHolder firstHolder = holders.get(0);
//                    // 如果是消息单元格
//                    if (firstHolder.itemView instanceof ChatMessageCell) {
//                        // 获取分组信息
////                        MessageObject.GroupedMessages group = ((ChatMessageCell) firstHolder.itemView).getCurrentMessagesGroup();
////                        // 如果分组不为空
////                        if (group != null) {
////                            // 检查分组大小是否小于等于移除的列表大小（即是否全部移除）
////                            wholeGroup = group.messages.size() <= holders.size();
////                        }
//                    }
//                    // 如果不是整个分组
//                    if (!wholeGroup) {
//                        // 回退到之前的动画逻辑，添加回挂起移除列表
//                        mPendingRemovals.addAll(holders);
//                    } else {
//                        // 执行分组移除动画
//                        animateRemoveGroupImpl(holders);
//                        // 标记有灭霸效果
//                        hadThanos = true;
//                    }
//                }
//            }
//        }
//        // 遍历剩余的挂起移除列表
//        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
//            // 检查是否触发灭霸效果
//            boolean thanos = toBeSnapped.remove(holder) && supportsThanos;
//            // 执行移除动画实现
//            animateRemoveImpl(holder, thanos);
//            // 如果触发了灭霸效果，标记为 true
//            if (thanos) {
//                hadThanos = true;
//            }
//        }
//        // 记录最终是否有灭霸效果
//        final boolean finalThanos = hadThanos;
//        // 清空挂起移除列表
//        mPendingRemovals.clear();
//        // 接下来，移动项目
//        // 如果有挂起的移动
//        if (movesPending) {
//            // 创建移动信息列表
//            final ArrayList<MoveInfo> moves = new ArrayList<>();
//            // 添加所有挂起的移动
//            moves.addAll(mPendingMoves);
//            // 添加到移动列表集合
//            mMovesList.add(moves);
//            // 清空挂起移动列表
//            mPendingMoves.clear();
//            // 创建移动执行器
//            Runnable mover = new Runnable() {
//                @Override
//                public void run() {
//                    // 遍历所有移动信息
//                    for (MoveInfo moveInfo : moves) {
//                        // 执行移动动画实现
//                        animateMoveImpl(moveInfo.holder, moveInfo, finalThanos);
//                    }
//                    // 清空移动列表
//                    moves.clear();
//                    // 从移动列表集合中移除
//                    mMovesList.remove(moves);
//                }
//            };
//            // 如果延迟动画且有移除操作
//            if (delayAnimations && removalsPending) {
//                // 获取第一个移动的视图
//                View view = moves.get(0).holder.itemView;
//                // 延迟执行移动动画
//                ViewCompat.postOnAnimationDelayed(view, mover, hadThanos ? 0 : getMoveAnimationDelay());
//            } else {
//                // 立即执行移动动画
//                mover.run();
//            }
//        }
//        // 接下来，改变项目，与移动动画并行运行
//        if (changesPending) {
//            // 创建改变信息列表
//            final ArrayList<ChangeInfo> changes = new ArrayList<>();
//            // 添加所有挂起的改变
//            changes.addAll(mPendingChanges);
//            // 添加到改变列表集合
//            mChangesList.add(changes);
//            // 清空挂起改变列表
//            mPendingChanges.clear();
//            // 创建改变执行器
//            Runnable changer = new Runnable() {
//                @Override
//                public void run() {
//                    // 遍历所有改变信息
//                    for (ChangeInfo change : changes) {
//                        // 执行改变动画实现
//                        animateChangeImpl(change);
//                    }
//                    // 清空改变列表
//                    changes.clear();
//                    // 从改变列表集合中移除
//                    mChangesList.remove(changes);
//                }
//            };
//            // 如果延迟动画且有移除操作
//            if (delayAnimations && removalsPending) {
//                // 获取旧的 ViewHolder
//                RecyclerView.ViewHolder holder = changes.get(0).oldHolder;
//                // 延迟执行改变动画
//                ViewCompat.postOnAnimationDelayed(holder.itemView, changer, 0);
//            } else {
//                // 立即执行改变动画
//                changer.run();
//            }
//        }
//        // 接下来，添加项目
//        if (additionsPending) {
//            // 创建添加列表
//            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>();
//            // 添加所有挂起的添加
//            additions.addAll(mPendingAdditions);
//            // 清空挂起添加列表
//            mPendingAdditions.clear();
//
//            // 重置 Alpha 进入延迟
//            alphaEnterDelay = 0;
//            // 按 Top 坐标排序（从下到上）
//            Collections.sort(additions, (i1, i2) -> i2.itemView.getTop() - i1.itemView.getTop());
//            // 遍历添加列表
//            for (RecyclerView.ViewHolder holder : additions) {
//                // 执行添加动画实现
//                animateAddImpl(holder);
//            }
//            // 清空添加列表
//            additions.clear();
//        }
//    }
//
//    // 运行消息进入过渡
//    private void runMessageEnterTransition() {
//        // 检查是否有挂起的移除
//        boolean removalsPending = !mPendingRemovals.isEmpty();
//        // 检查是否有挂起的移动
//        boolean movesPending = !mPendingMoves.isEmpty();
//        // 检查是否有挂起的改变
//        boolean changesPending = !mPendingChanges.isEmpty();
//        // 检查是否有挂起的添加
//        boolean additionsPending = !mPendingAdditions.isEmpty();
//
//        // 如果没有任何挂起的动画，直接返回
//        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
//            return;
//        }
//
//        // 计算新增项的高度总和
//        int addedItemsHeight = 0;
//        // 遍历挂起的添加列表
//        for (int i = 0; i < mPendingAdditions.size(); i++) {
//            // 获取视图
//            View view = mPendingAdditions.get(i).itemView;
//            // 如果是消息单元格
//            if (view instanceof ChatMessageCell) {
//                // 获取单元格
//                ChatMessageCell cell = ((ChatMessageCell) view);
//                // 如果当前位置不是左侧（即自己发送的消息），跳过计算高度（可能不需要从底部推入那么多）
//                // 注意：这里逻辑似乎是跳过非左侧消息的高度累加？或者只累加右侧？
//                // 实际上代码是：如果不是左侧（即右侧），continue。
//                // 所以只累加左侧消息的高度？或者反之？
//                // MessageObject.POSITION_FLAG_LEFT 通常表示左侧消息（接收到的）。
//                // 如果 (flags & LEFT) == 0，表示不是左侧，即右侧（发送出的）。
//                // 所以如果是发送出的消息，continue。
//                // 这意味着只累加接收到的消息的高度？
//                if (cell.getCurrentPosition() != null && (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
//                    continue;
//                }
//            }
//            // 累加高度
//            addedItemsHeight += mPendingAdditions.get(i).itemView.getHeight();
//        }
//
//        // 执行所有移除动画
//        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
//            animateRemoveImpl(holder);
//        }
//        // 清空挂起移除列表
//        mPendingRemovals.clear();
//        // 如果有移动
//        if (movesPending) {
//            // 创建移动列表
//            final ArrayList<MoveInfo> moves = new ArrayList<>();
//            // 添加所有挂起的移动
//            moves.addAll(mPendingMoves);
//            // 清空挂起移动列表
//            mPendingMoves.clear();
//            // 遍历并执行移动动画
//            for (MoveInfo moveInfo : moves) {
//                animateMoveImpl(moveInfo.holder, moveInfo);
//            }
//            // 清空移动列表
//            moves.clear();
//        }
//
//        // 如果有添加
//        if (additionsPending) {
//            // 创建添加列表
//            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>();
//            // 添加所有挂起的添加
//            additions.addAll(mPendingAdditions);
//            // 清空挂起添加列表
//            mPendingAdditions.clear();
//
//            // 遍历并执行添加动画，传入新增项的高度
//            for (RecyclerView.ViewHolder holder : additions) {
//                animateAddImpl(holder, addedItemsHeight);
//            }
//            // 清空添加列表
//            additions.clear();
//        }
//    }
//
//    @Override
//    // 动画出现（preLayout 到 postLayout）
//    public boolean animateAppearance(@NonNull RecyclerView.ViewHolder viewHolder, @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
//        // 调用父类实现
//        boolean res = super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo);
//        // 如果父类返回 true 且应该从底部动画进入
//        if (res && shouldAnimateEnterFromBottom) {
//            // 标记是否运行从底部平移
//            boolean runTranslationFromBottom = false;
//            // 遍历挂起的添加列表
//            for (int i = 0; i < mPendingAdditions.size(); i++) {
//
//                // 如果是第一项
//                if (mPendingAdditions.get(i).getLayoutPosition() == 0) {
//                    // 标记为从底部进入
//                    runTranslationFromBottom = true;
//                }
//            }
//            // 计算新增项的高度
//            int addedItemsHeight = 0;
//            // 如果从底部平移
//            if (runTranslationFromBottom) {
//                // 遍历并累加高度
//                for (int i = 0; i < mPendingAdditions.size(); i++) {
//                    addedItemsHeight += mPendingAdditions.get(i).itemView.getHeight();
//                }
//            }
//
//            // 设置所有新增项的 Y 平移
//            for (int i = 0; i < mPendingAdditions.size(); i++) {
//                mPendingAdditions.get(i).itemView.setTranslationY(addedItemsHeight);
//            }
//        }
//        // 返回结果
//        return res;
//    }
//
//    @Override
//    // 动画添加
//    public boolean animateAdd(RecyclerView.ViewHolder holder) {
//        // 重置动画状态
//        resetAnimation(holder);
//        // 设置初始 Alpha 为 0
//        holder.itemView.setAlpha(0);
//        // 如果不从底部动画进入
//        if (!shouldAnimateEnterFromBottom) {
//            // 设置初始缩放
//            holder.itemView.setScaleX(0.9f);
//            holder.itemView.setScaleY(0.9f);
//        } else {
//            // 如果是消息单元格
//            if (holder.itemView instanceof ChatMessageCell) {
//                // 标记消息正在进入
//                ((ChatMessageCell) holder.itemView).getTransitionParams().messageEntering = true;
//            }
//        }
//        // 添加到挂起添加列表
//        mPendingAdditions.add(holder);
//        // 返回 true 表示稍后处理
//        return true;
//    }
//
//    // 添加动画实现
//    public void animateAddImpl(final RecyclerView.ViewHolder holder, int addedItemsHeight) {
//        // 获取视图
//        final View view = holder.itemView;
//        // 获取 ViewPropertyAnimator
//        final ViewPropertyAnimator animation = view.animate();
//        // 添加到正在添加动画列表
//        mAddAnimations.add(holder);
//        // 设置 Y 平移
//        view.setTranslationY(addedItemsHeight);
//        // 设置缩放为 1
//        holder.itemView.setScaleX(1);
//        holder.itemView.setScaleY(1);
//        // 获取 ChatMessageCell
//        ChatMessageCell chatMessageCell = holder.itemView instanceof ChatMessageCell ? (ChatMessageCell) holder.itemView : null;
//        // 如果不是忽略 Alpha 的消息单元格，设置 Alpha 为 1
//        if (!(chatMessageCell != null && chatMessageCell.getTransitionParams().ignoreAlpha)) {
//            holder.itemView.setAlpha(1);
//        }
//        // 如果有 Activity 且正在动画的消息对象包含此消息
//        if (activity != null && chatMessageCell != null && activity.animatingMessageObjects.contains(chatMessageCell.getMessageObject())) {
//            // 移除动画消息对象
//            activity.animatingMessageObjects.remove(chatMessageCell.getMessageObject());
//            // 如果可以显示消息过渡
//            if (activity.getChatActivityEnterView().canShowMessageTransition()) {
//                // 如果是语音消息
//                if (chatMessageCell.getMessageObject().isVoice()) {
//                    // 如果平移距离合理
//                    if (Math.abs(view.getTranslationY()) < view.getMeasuredHeight() * 3f) {
//                        // 创建语音消息进入过渡
//                        VoiceMessageEnterTransition transition = new VoiceMessageEnterTransition(chatMessageCell, activity.getChatActivityEnterView(), recyclerListView, activity.messageEnterTransitionContainer, resourcesProvider);
//                        // 开始过渡
//                        transition.start();
//                    }
//                } else {
//                    // 如果设备性能足够且平移距离合理
//                    if (SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW && Math.abs(view.getTranslationY()) < recyclerListView.getMeasuredHeight()) {
//                        // 创建文本消息进入过渡
//                        TextMessageEnterTransition transition = new TextMessageEnterTransition(chatMessageCell, activity, recyclerListView, activity.messageEnterTransitionContainer, resourcesProvider);
//                        // 开始过渡
//                        transition.start();
//                    }
//                }
//                // 开始输入框的消息过渡动画
//                activity.getChatActivityEnterView().startMessageTransition();
//            }
//        }
//        // 执行平移回 0 的动画
//        animation.translationY(0).setDuration(getMoveDuration())
//                .setInterpolator(translationInterpolator)
//                .setListener(new AnimatorListenerAdapter() {
//                    @Override
//                    // 动画开始
//                    public void onAnimationStart(Animator animator) {
//                        dispatchAddStarting(holder);
//                    }
//
//                    @Override
//                    // 动画取消
//                    public void onAnimationCancel(Animator animator) {
//                        // 重置平移
//                        view.setTranslationY(0);
//                        // 重置消息进入标记
//                        if (view instanceof ChatMessageCell) {
//                            ((ChatMessageCell) view).getTransitionParams().messageEntering = false;
//                        }
//                    }
//
//                    @Override
//                    // 动画结束
//                    public void onAnimationEnd(Animator animator) {
//                        // 重置消息进入标记
//                        if (view instanceof ChatMessageCell) {
//                            ((ChatMessageCell) view).getTransitionParams().messageEntering = false;
//                        }
//                        // 清除监听器
//                        animation.setListener(null);
//                        // 从列表移除并分发结束
//                        if (mAddAnimations.remove(holder)) {
//                            dispatchAddFinished(holder);
//                            dispatchFinishedWhenDone();
//                        }
//                    }
//                }).start();
//    }
//
//    @Override
//    // 动画移除
//    public boolean animateRemove(RecyclerView.ViewHolder holder, ItemHolderInfo info) {
//        // 如果启用日志，记录移除动画
//        // 调用父类实现
//        boolean rez = super.animateRemove(holder, info);
//        // 如果父类返回 true
//        if (rez) {
//            // 如果有布局信息
//            if (info != null) {
//                // 获取移除前的 Top 坐标
//                int fromY = info.top;
//                // 获取移除后的 Top 坐标
//                int toY = holder.itemView.getTop();
//
//                // 获取移除前的 Left 坐标
//                int fromX = info.left;
//                // 获取移除后的 Left 坐标
//                int toX = holder.itemView.getLeft();
//
//                // 计算 X 轴位移
//                int deltaX = toX - fromX;
//                // 计算 Y 轴位移
//                int deltaY = toY - fromY;
//
//                // 如果 Y 轴有位移
//                if (deltaY != 0) {
//                    // 设置 Y 平移
//                    holder.itemView.setTranslationY(-deltaY);
//                }
//
//                // 如果是消息单元格
//                if (holder.itemView instanceof ChatMessageCell) {
//                    // 转换为 ChatMessageCell
//                    ChatMessageCell chatMessageCell = (ChatMessageCell) holder.itemView;
//                    // 如果 X 轴有位移
//                    if (deltaX != 0) {
//                        // 设置动画 X 偏移
//                        chatMessageCell.setAnimationOffsetX(-deltaX);
//                    }
//                    // 如果是扩展的布局信息
//                    if (info instanceof ItemHolderInfoExtended) {
//                        // 转换为扩展信息
//                        ItemHolderInfoExtended infoExtended = ((ItemHolderInfoExtended) info);
//                        // 设置图片坐标
//                        chatMessageCell.setImageCoords(infoExtended.imageX, infoExtended.imageY, infoExtended.imageWidth, infoExtended.imageHeight);
//                    }
//                } else {
//                    // 如果不是消息单元格且 X 轴有位移
//                    if (deltaX != 0) {
//                        // 设置 X 平移
//                        holder.itemView.setTranslationX(-deltaX);
//                    }
//                }
//            }
//        }
//        // 返回结果
//        return rez;
//    }
//
//    @Override
//    // 动画移动
//    public boolean animateMove(RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
//        // 获取视图
//        final View view = holder.itemView;
//        // 消息单元格引用
//        ChatMessageCell chatMessageCell = null;
//        // 聊天动作单元格引用
//        ChatActionCell chatActionCell = null;
//        // 检查是否为消息单元格
//        if (holder.itemView instanceof ChatMessageCell) {
//            // 转换为消息单元格
//            chatMessageCell = ((ChatMessageCell) holder.itemView);
//            // 调整起始 X 坐标
//            fromX += (int) chatMessageCell.getAnimationOffsetX();
//            // 如果上一次顶部偏移与当前顶部偏移不同
//            if (chatMessageCell.getTransitionParams().lastTopOffset != chatMessageCell.getTopMediaOffset()) {
//                // 调整起始 Y 坐标
//                fromY += chatMessageCell.getTransitionParams().lastTopOffset - chatMessageCell.getTopMediaOffset();
//            }
//        } else if (holder.itemView instanceof ChatActionCell) {
//            // 如果是聊天动作单元格
//            chatActionCell = ((ChatActionCell) holder.itemView);
//            // 调整起始 X 坐标
//            fromX += (int) holder.itemView.getTranslationX();
//        } else {
//            // 其他情况调整起始 X 坐标
//            fromX += (int) holder.itemView.getTranslationX();
//        }
//        // 调整起始 Y 坐标
//        fromY += (int) holder.itemView.getTranslationY();
//        // 初始化图片坐标和尺寸
//        float imageX = 0;
//        float imageY = 0;
//        float imageW = 0;
//        float imageH = 0;
//        // 初始化圆角半径数组
//        int[] roundRadius = new int[4];
//        // 如果是消息单元格
//        if (chatMessageCell != null) {
//            // 获取图片坐标和尺寸
//            imageX = chatMessageCell.getPhotoImage().getImageX();
//            imageY = chatMessageCell.getPhotoImage().getImageY();
//            imageW = chatMessageCell.getPhotoImage().getImageWidth();
//            imageH = chatMessageCell.getPhotoImage().getImageHeight();
//            // 获取圆角半径
//            for (int i = 0; i < 4; i++) {
//                roundRadius[i] = chatMessageCell.getPhotoImage().getRoundRadius()[i];
//            }
//        }
//        // 重置动画状态
//        resetAnimation(holder);
//        // 计算 X 位移
//        int deltaX = toX - fromX;
//        // 计算 Y 位移
//        int deltaY = toY - fromY;
//        // 如果 Y 位移不为 0
//        if (deltaY != 0) {
//            // 设置 Y 平移
//            view.setTranslationY(-deltaY);
//        }
//
//        // 创建扩展移动信息
//        MoveInfoExtended moveInfo = new MoveInfoExtended(holder, fromX, fromY, toX, toY);
//
//        // 如果是消息单元格
//        if (chatMessageCell != null) {
//            // 获取过渡参数
//            ChatMessageCell.TransitionParams params = chatMessageCell.getTransitionParams();
//
//            // 如果不支持改变动画
//            if (!params.supportChangeAnimation()) {
//                // 如果没有位移
//                if (deltaX == 0 && deltaY == 0) {
//                    // 分发移动结束
//                    dispatchMoveFinished(holder);
//                    return false;
//                }
//                // 如果有 X 位移
//                if (deltaX != 0) {
//                    // 设置 X 平移
//                    view.setTranslationX(-deltaX);
//                }
//                // 添加到挂起移动列表
//                mPendingMoves.add(moveInfo);
//                // 检查是否正在运行
//                checkIsRunning();
//                return true;
//            }
//
//            // 获取当前消息分组
//            MessageObject.GroupedMessages group = chatMessageCell.getCurrentMessagesGroup();
//
//            // 如果有 X 位移
//            if (deltaX != 0) {
//                // 设置动画 X 偏移
//                chatMessageCell.setAnimationOffsetX(-deltaX);
//            }
//
//            // 如果有扩展布局信息
////            if (info instanceof ItemHolderInfoExtended) {
////                // 获取新图片接收器
////                ImageReceiver newImage = chatMessageCell.getPhotoImage();
////                // 转换为扩展信息
////                ItemHolderInfoExtended infoExtended = ((ItemHolderInfoExtended) info);
////                // 判断是否需要动画图片
////                moveInfo.animateImage = params.wasDraw && infoExtended.imageHeight != 0 && infoExtended.imageWidth != 0;
////                // 如果需要动画图片
////                if (moveInfo.animateImage) {
////                    // 允许子视图绘制到边界外
////                    recyclerListView.setClipChildren(false);
////                    // 刷新列表视图
////                    recyclerListView.invalidate();
////
////                    // 标记图片边界过渡
////                    params.imageChangeBoundsTransition = true;
////                    // 如果是圆视频
////                    if (chatMessageCell.getMessageObject().isRoundVideo()) {
////                        // 设置目标图片坐标和尺寸
////                        params.animateToImageX = imageX;
////                        params.animateToImageY = imageY;
////                        params.animateToImageW = imageW;
////                        params.animateToImageH = imageH;
////                        params.animateToRadius = roundRadius;
////                    } else {
////                        // 设置目标图片坐标和尺寸
////                        params.animateToImageX = newImage.getImageX();
////                        params.animateToImageY = newImage.getImageY();
////                        params.animateToImageW = newImage.getImageWidth();
////                        params.animateToImageH = newImage.getImageHeight();
////                        params.animateToRadius = newImage.getRoundRadius();
////                    }
////
////                    // 检查是否需要动画圆角半径
////                    params.animateRadius = false;
////                    for (int i = 0; i < 4; i++) {
////                        if (params.imageRoundRadius[i] != params.animateToRadius[i]) {
////                            params.animateRadius = true;
////                            break;
////                        }
////                    }
////                    // 如果图片坐标尺寸未变且不需要动画圆角
////                    if (params.animateToImageX == infoExtended.imageX && params.animateToImageY == infoExtended.imageY &&
////                            params.animateToImageH == infoExtended.imageHeight && params.animateToImageW == infoExtended.imageWidth && !params.animateRadius) {
////                        // 取消图片边界过渡
////                        params.imageChangeBoundsTransition = false;
////                        moveInfo.animateImage = false;
////                    } else {
////                        // 设置移动信息中的图片参数
////                        moveInfo.imageX = infoExtended.imageX;
////                        moveInfo.imageY = infoExtended.imageY;
////                        moveInfo.imageWidth = infoExtended.imageWidth;
////                        moveInfo.imageHeight = infoExtended.imageHeight;
////
////                        // 如果有分组且标题布局状态不一致
////                        if (group != null && group.hasCaption != group.transitionParams.drawCaptionLayout) {
////                            // 设置标题进入进度
////                            group.transitionParams.captionEnterProgress = group.transitionParams.drawCaptionLayout ? 1f : 0;
////                        }
////                        // 如果需要动画圆角半径
////                        if (params.animateRadius) {
////                            // 如果目标圆角与当前圆角相同，复制一份
////                            if (params.animateToRadius == newImage.getRoundRadius()) {
////                                params.animateToRadius = new int[4];
////                                for (int i = 0; i < 4; i++) {
////                                    params.animateToRadius[i] = newImage.getRoundRadius()[i];
////                                }
////                            }
////                            // 设置新的圆角半径
////                            newImage.setRoundRadius(params.imageRoundRadius);
////                        }
////                        // 设置图片坐标
////                        chatMessageCell.setImageCoords(moveInfo.imageX, moveInfo.imageY, moveInfo.imageWidth, moveInfo.imageHeight);
////                    }
////                }
////
////                // 如果没有分组且之前绘制过
////                if (group == null && params.wasDraw) {
////                    // 判断是否是发出的消息
////                    boolean isOut = chatMessageCell.getMessageObject().isOutOwner();
////                    // 判断宽度是否改变
////                    boolean widthChanged = (isOut && params.lastDrawingBackgroundRect.left != chatMessageCell.getBackgroundDrawableLeft()) ||
////                            (!isOut && params.lastDrawingBackgroundRect.right != chatMessageCell.getBackgroundDrawableRight());
////                    // 如果宽度改变或顶部/底部改变
////                    if (widthChanged ||
////                            params.lastDrawingBackgroundRect.top != chatMessageCell.getBackgroundDrawableTop() ||
////                            params.lastDrawingBackgroundRect.bottom != chatMessageCell.getBackgroundDrawableBottom()) {
////                        // 计算背景位移
////                        moveInfo.deltaBottom = chatMessageCell.getBackgroundDrawableBottom() - params.lastDrawingBackgroundRect.bottom;
////                        moveInfo.deltaTop = chatMessageCell.getBackgroundDrawableTop() - params.lastDrawingBackgroundRect.top;
////                        // 如果侧边菜单状态改变
////                        if (chatMessageCell.isSideMenuEnabled != params.lastDrawingSideMenuEnabled) {
////                            // 计算左右位移
////                            moveInfo.deltaLeft = (chatMessageCell.getBackgroundDrawableLeft() - params.lastDrawingBackgroundRect.left);
////                            moveInfo.deltaRight = (chatMessageCell.getBackgroundDrawableRight() - params.lastDrawingBackgroundRect.right);
////                        } else if (isOut) {
////                            // 如果是发出的消息，计算左侧位移
////                            moveInfo.deltaLeft = chatMessageCell.getBackgroundDrawableLeft() - params.lastDrawingBackgroundRect.left;
////                        } else {
////                            // 如果是接收的消息，计算右侧位移
////                            moveInfo.deltaRight = chatMessageCell.getBackgroundDrawableRight() - params.lastDrawingBackgroundRect.right;
////                        }
////                        // 标记只动画背景
////                        moveInfo.animateBackgroundOnly = true;
////
////                        // 设置背景内部边界动画
////                        params.animateBackgroundBoundsInner = true;
////                        // 设置背景宽度动画
////                        params.animateBackgroundWidth = widthChanged;
////                        // 设置位移参数
////                        params.deltaLeft = -moveInfo.deltaLeft;
////                        params.deltaRight = -moveInfo.deltaRight;
////                        params.deltaTop = -moveInfo.deltaTop;
////                        params.deltaBottom = -moveInfo.deltaBottom;
////
////                        // 允许子视图绘制到边界外并刷新
////                        recyclerListView.setClipChildren(false);
////                        recyclerListView.invalidate();
////                    }
////                }
////            }
//
//            // 如果有分组
////            if (group != null) {
////                // 如果分组在将要改变的分组列表中
////                if (willChangedGroups.contains(group)) {
////                    // 移除该分组
////                    willChangedGroups.remove(group);
////                    // 获取 RecyclerListView
////                    RecyclerListView recyclerListView = (RecyclerListView) holder.itemView.getParent();
////                    // 初始化动画目标边界
////                    int animateToLeft = 0;
////                    int animateToRight = 0;
////                    int animateToTop = 0;
////                    int animateToBottom = 0;
////                    // 标记是否所有可见项都被删除
////                    boolean allVisibleItemsDeleted = true;
////
////                    // 获取分组过渡参数
////                    MessageObject.GroupedMessages.TransitionParams groupTransitionParams = group.transitionParams;
////                    // 上一个绘制的单元格
////                    ChatMessageCell lastDrawingCell = null;
////                    // 遍历子视图
////                    for (int i = 0; i < recyclerListView.getChildCount(); i++) {
////                        View child = recyclerListView.getChildAt(i);
////
////                        // 如果是消息单元格
////                        if (child instanceof ChatMessageCell) {
////                            ChatMessageCell cell = (ChatMessageCell) child;
////                            // 如果属于当前分组且未被删除
////                            if (cell.getCurrentMessagesGroup() == group && !cell.getMessageObject().deleted) {
////
////                                // 计算边界
////                                int left = cell.getLeft() + cell.getBackgroundDrawableLeft();
////                                int right = cell.getLeft() + cell.getBackgroundDrawableRight();
////                                int top = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableTop();
////                                int bottom = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom();
////
////                                // 更新目标左边界
////                                if (animateToLeft == 0 || left < animateToLeft) {
////                                    animateToLeft = left;
////                                }
////
////                                // 更新目标右边界
////                                if (animateToRight == 0 || right > animateToRight) {
////                                    animateToRight = right;
////                                }
////
////                                // 如果之前绘制过或是新分组
////                                if (cell.getTransitionParams().wasDraw || groupTransitionParams.isNewGroup) {
////                                    lastDrawingCell = cell;
////                                    allVisibleItemsDeleted = false;
////                                    // 更新目标上边界
////                                    if (animateToTop == 0 || top < animateToTop) {
////                                        animateToTop = top;
////                                    }
////                                    // 更新目标下边界
////                                    if (animateToBottom == 0 || bottom > animateToBottom) {
////                                        animateToBottom = bottom;
////                                    }
////                                }
////                            }
////                        }
////                    }
////
////                    // 标记为非新分组
////                    groupTransitionParams.isNewGroup = false;
////
////                    // 如果所有边界都没有变化
////                    if (animateToTop == 0 &&  animateToBottom == 0 && animateToLeft == 0 && animateToRight == 0) {
////                        // 不动画改变分组背景
////                        moveInfo.animateChangeGroupBackground = false;
////                        // 不改变背景边界
////                        groupTransitionParams.backgroundChangeBounds = false;
////                    } else {
////                        // 计算分组偏移量
////                        moveInfo.groupOffsetTop = -animateToTop + groupTransitionParams.top;
////                        moveInfo.groupOffsetBottom = -animateToBottom + groupTransitionParams.bottom;
////                        moveInfo.groupOffsetLeft = -animateToLeft + groupTransitionParams.left;
////                        moveInfo.groupOffsetRight = -animateToRight + groupTransitionParams.right;
////
////                        // 标记动画改变分组背景
////                        moveInfo.animateChangeGroupBackground = true;
////                        // 标记背景改变边界
////                        groupTransitionParams.backgroundChangeBounds = true;
////                        // 设置分组偏移量
////                        groupTransitionParams.offsetTop = moveInfo.groupOffsetTop;
////                        groupTransitionParams.offsetBottom = moveInfo.groupOffsetBottom;
////                        groupTransitionParams.offsetLeft = moveInfo.groupOffsetLeft;
////                        groupTransitionParams.offsetRight = moveInfo.groupOffsetRight;
////
////                        // 设置标题进入进度
////                        groupTransitionParams.captionEnterProgress = groupTransitionParams.drawCaptionLayout ? 1f : 0f;
////
////                        // 允许子视图绘制到边界外并刷新
////                        recyclerListView.setClipChildren(false);
////                        recyclerListView.invalidate();
////                    }
////
////                    // 设置是否为删除的项目绘制背景
////                    groupTransitionParams.drawBackgroundForDeletedItems = allVisibleItemsDeleted;
////                }
////            }
//
//            // 检查是否在将要移除的分组中
////            MessageObject.GroupedMessages removedGroup = willRemovedGroup.get(chatMessageCell.getMessageObject().getId());
////            // 如果在移除分组中
////            if (removedGroup != null) {
////                // 获取分组过渡参数
////                MessageObject.GroupedMessages.TransitionParams groupTransitionParams = removedGroup.transitionParams;
////                // 从映射中移除
////                willRemovedGroup.remove(chatMessageCell.getMessageObject().getId());
////                // 如果之前绘制过
////                if (params.wasDraw) {
////                    // 当分组转换为单条消息时调用
////                    // 计算目标边界
////                    int animateToLeft = chatMessageCell.getLeft() + chatMessageCell.getBackgroundDrawableLeft();
////                    int animateToRight = chatMessageCell.getLeft() + chatMessageCell.getBackgroundDrawableRight();
////                    int animateToTop = chatMessageCell.getTop() + chatMessageCell.getPaddingTop() + chatMessageCell.getBackgroundDrawableTop();
////                    int animateToBottom = chatMessageCell.getTop() + chatMessageCell.getPaddingTop() + chatMessageCell.getBackgroundDrawableBottom();
////
////                    // 设置背景内部边界动画和移除分组动画
////                    params.animateBackgroundBoundsInner = moveInfo.animateRemoveGroup = true;
////                    // 计算位移
////                    moveInfo.deltaLeft = animateToLeft - groupTransitionParams.left;
////                    moveInfo.deltaRight = animateToRight - groupTransitionParams.right;
////                    moveInfo.deltaTop = animateToTop - groupTransitionParams.top;
////                    moveInfo.deltaBottom = animateToBottom - groupTransitionParams.bottom;
////                    // 不仅仅动画背景
////                    moveInfo.animateBackgroundOnly = false;
////
////                    // 设置参数位移
////                    params.deltaLeft = (int) (-moveInfo.deltaLeft - chatMessageCell.getAnimationOffsetX());
////                    params.deltaRight = (int) (-moveInfo.deltaRight - chatMessageCell.getAnimationOffsetX());
////                    params.deltaTop = (int) (-moveInfo.deltaTop - chatMessageCell.getTranslationY());
////                    params.deltaBottom = (int) (-moveInfo.deltaBottom - chatMessageCell.getTranslationY());
////                    // 标记分组转单条消息
////                    params.transformGroupToSingleMessage = true;
////
////                    // 允许子视图绘制到边界外并刷新
////                    recyclerListView.setClipChildren(false);
////                    recyclerListView.invalidate();
////                } else {
////                    // 如果没绘制过，为删除项绘制背景
////                    groupTransitionParams.drawBackgroundForDeletedItems = true;
////                }
////            }
//            // 检查是否绘制置顶底部
//            boolean drawPinnedBottom = chatMessageCell.isDrawPinnedBottom();x
//            // 如果绘制状态改变
//            if (params.drawPinnedBottomBackground != drawPinnedBottom) {
//                // 标记动画置顶底部
//                moveInfo.animatePinnedBottom = true;
//                // 重置进度
//                params.changePinnedBottomProgress = 0;
//            }
//
//            // 检查是否有内部改变动画
//            moveInfo.animateChangeInternal = params.animateChange();
//            // 如果有内部改变动画
//            if (moveInfo.animateChangeInternal) {
//                // 启用改变动画
//                params.animateChange = true;
//                // 重置进度
//                params.animateChangeProgress = 0f;
//            }
//
//            // 如果没有位移且没有其他动画
//            if (deltaX == 0 && deltaY == 0 && !moveInfo.animateImage && !moveInfo.animateRemoveGroup && !moveInfo.animateChangeGroupBackground && !moveInfo.animatePinnedBottom && !moveInfo.animateBackgroundOnly && !moveInfo.animateChangeInternal) {
//                // 分发移动结束
//                dispatchMoveFinished(holder);
//                return false;
//            }
//        } else if (chatActionCell != null) {
//            // 如果是聊天动作单元格
//            ChatActionCell.TransitionParams params = chatActionCell.getTransitionParams();
//
//            // 如果不支持改变动画
//            if (!params.supportChangeAnimation()) {
//                // 如果没有位移
//                if (deltaX == 0 && deltaY == 0) {
//                    // 分发移动结束
//                    dispatchMoveFinished(holder);
//                    return false;
//                }
//                // 如果有 X 位移
//                if (deltaX != 0) {
//                    // 设置 X 平移
//                    view.setTranslationX(-deltaX);
//                }
//                // 添加到挂起移动列表
//                mPendingMoves.add(moveInfo);
//                // 检查是否正在运行
//                checkIsRunning();
//                return true;
//            }
//
//            // 如果有 X 位移
//            if (deltaX != 0) {
//                // 设置 X 平移
//                view.setTranslationX(-deltaX);
//            }
//
//            // 检查是否有内部改变动画
//            moveInfo.animateChangeInternal = params.animateChange();
//            // 如果有内部改变动画
//            if (moveInfo.animateChangeInternal) {
//                // 启用改变动画
//                params.animateChange = true;
//                // 重置进度
//                params.animateChangeProgress = 0f;
//            }
//
//            // 如果没有位移且没有内部改变动画
//            if (deltaX == 0 && deltaY == 0 && !moveInfo.animateChangeInternal) {
//                // 分发移动结束
//                dispatchMoveFinished(holder);
//                return false;
//            }
//        } else if (holder.itemView instanceof BotHelpCell) {
//            // 如果是机器人帮助单元格
//            BotHelpCell botInfo = (BotHelpCell) holder.itemView;
//            // 设置正在动画
//            botInfo.setAnimating(true);
//        } else if (holder.itemView instanceof UserInfoCell) {
//            // 如果是用户信息单元格
//            UserInfoCell cell = (UserInfoCell) holder.itemView;
//            // 设置正在动画
//            cell.setAnimating(true);
//        } else {
//            // 其他情况
//            // 如果没有位移
//            if (deltaX == 0 && deltaY == 0) {
//                // 分发移动结束
//                dispatchMoveFinished(holder);
//                return false;
//            }
//            // 如果有 X 位移
//            if (deltaX != 0) {
//                // 设置 X 平移
//                view.setTranslationX(-deltaX);
//            }
//        }
//
//        // 添加到挂起移动列表
//        mPendingMoves.add(moveInfo);
//        // 检查是否正在运行
//        checkIsRunning();
//        return true;
//    }
//
//    @Override
//    // 动画移动实现
//    protected void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo) {
//        animateMoveImpl(holder, moveInfo, false);
//    }
//    // 动画移动实现（带灭霸效果参数）
//    protected void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo, boolean withThanos) {
//        // 获取起始坐标
//        int fromX = moveInfo.fromX;
//        int fromY = moveInfo.fromY;
//        int toX = moveInfo.toX;
//        int toY = moveInfo.toY;
//        // 获取视图
//        final View view = holder.itemView;
//        // 计算 Y 位移
//        final int deltaY = toY - fromY;
//
//        // 创建动画集合
//        AnimatorSet animatorSet = new AnimatorSet();
//
//        // 如果 Y 位移不为 0
//        if (deltaY != 0) {
//            // 添加 Y 平移回 0 的动画
//            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0));
//        }
//        // 添加到正在移动动画列表
//        mMoveAnimations.add(holder);
//
//        // 转换为扩展移动信息
//        MoveInfoExtended moveInfoExtended = (MoveInfoExtended) moveInfo;
//
//        // 如果有 Activity 且是机器人帮助单元格
////        if (activity != null && holder.itemView instanceof BotHelpCell) {
////            // 转换为 BotHelpCell
////            BotHelpCell botCell = (BotHelpCell) holder.itemView;
////            // 获取起始 Y 平移
////            float animateFrom = botCell.getTranslationY();
////
////            // 创建 ValueAnimator
////            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
////            // 添加更新监听器
////            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
////                @Override
////                public void onAnimationUpdate(ValueAnimator valueAnimator) {
////                    // 获取动画进度
////                    float v = (float) valueAnimator.getAnimatedValue();
////                    // 计算顶部位置
////                    float top = (recyclerListView.getMeasuredHeight() - activity.getChatListViewPadding() - activity.blurredViewBottomOffset) / 2f - botCell.getMeasuredHeight() / 2f + activity.getChatListViewPadding();
////                    float animateTo = 0;
////                    // 如果当前顶部大于计算的顶部
////                    if (botCell.getTop() > top) {
////                        animateTo = top - botCell.getTop();
////                    }
////                    // 设置 Y 平移
////                    botCell.setTranslationY(animateFrom * (1f - v) + animateTo * v);
////                }
////            });
////            // 添加到动画集合
////            animatorSet.playTogether(valueAnimator);
////        } else
////            if (activity != null && holder.itemView instanceof UserInfoCell) {
////            // 如果是用户信息单元格
////            UserInfoCell cell = (UserInfoCell) holder.itemView ;
////            // 获取起始 Y 平移
////            float animateFrom = cell.getTranslationY();
////
////            // 创建 ValueAnimator
////            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
////            // 添加更新监听器
////            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
////                @Override
////                public void onAnimationUpdate(ValueAnimator valueAnimator) {
////                    // 获取动画进度
////                    float v = (float) valueAnimator.getAnimatedValue();
////                    // 计算顶部位置
////                    float top = (recyclerListView.getMeasuredHeight() - activity.getChatListViewPadding() - activity.blurredViewBottomOffset) / 2f - cell.getMeasuredHeight() / 2f + activity.getChatListViewPadding();
////                    float animateTo = 0;
////                    // 如果当前顶部大于计算的顶部
////                    if (cell.getTop() > top) {
////                        animateTo = top - cell.getTop();
////                    }
////                    // 设置 Y 平移
////                    cell.setTranslationY(animateFrom * (1f - v) + animateTo * v);
////                }
////            });
////            // 添加到动画集合
////            animatorSet.playTogether(valueAnimator);
////        } else
//
//            if (holder.itemView instanceof ChatMessageCell) {
//            // 如果是消息单元格
//            ChatMessageCell chatMessageCell = (ChatMessageCell) holder.itemView;
//            // 获取过渡参数
//            ChatMessageCell.TransitionParams params = chatMessageCell.getTransitionParams();
//            // 创建 X 偏移回 0 的动画
//            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(chatMessageCell, chatMessageCell.ANIMATION_OFFSET_X, 0);
//            // 添加到动画集合
//            animatorSet.playTogether(objectAnimator);
//
//            // 如果需要动画图片
//            if (moveInfoExtended.animateImage) {
//                // 设置初始图片坐标
//                chatMessageCell.setImageCoords(moveInfoExtended.imageX, moveInfoExtended.imageY, moveInfoExtended.imageWidth, moveInfoExtended.imageHeight);
//                // 创建 ValueAnimator
//                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
//
//
//                // 获取标题进入进度的起始值
//                float captionEnterFrom = chatMessageCell.getCurrentMessagesGroup() == null ? params.captionEnterProgress : chatMessageCell.getCurrentMessagesGroup().transitionParams.captionEnterProgress;
//                // 获取标题进入进度的目标值
//                float captionEnterTo = chatMessageCell.getCurrentMessagesGroup() == null ? (chatMessageCell.hasCaptionLayout()  ? 1 : 0) : (chatMessageCell.getCurrentMessagesGroup().hasCaption ? 1 : 0);
//                // 判断是否动画标题
//                boolean animateCaption = captionEnterFrom != captionEnterTo;
//
//                // 初始化起始圆角半径
//                int[] fromRoundRadius = null;
//                // 如果需要动画圆角
//                if (params.animateRadius) {
//                    fromRoundRadius = new int[4];
//                    for (int i = 0; i < 4; i++) {
//                        fromRoundRadius[i] = chatMessageCell.getPhotoImage().getRoundRadius()[i];
//                    }
//                }
//
//                // 最终起始圆角半径
//                int[] finalFromRoundRadius = fromRoundRadius;
//
//                // 添加更新监听器
//                valueAnimator.addUpdateListener(animation -> {
//                    // 获取动画进度
//                    float v = (float) animation.getAnimatedValue();
//                    // 插值计算图片坐标和尺寸
//                    float x = moveInfoExtended.imageX * (1f - v) + params.animateToImageX * v;
//                    float y = moveInfoExtended.imageY * (1f - v) + params.animateToImageY * v;
//                    float width = moveInfoExtended.imageWidth * (1f - v) + params.animateToImageW * v;
//                    float height = moveInfoExtended.imageHeight * (1f - v) + params.animateToImageH * v;
//
//                    // 如果动画标题
//                    if (animateCaption) {
//                        float captionP = captionEnterFrom * (1f - v) + captionEnterTo * v;
//                        params.captionEnterProgress = captionP;
//                        if (chatMessageCell.getCurrentMessagesGroup() != null) {
//                            chatMessageCell.getCurrentMessagesGroup().transitionParams.captionEnterProgress = captionP;
//                        }
//                    }
//
//
//                    // 如果动画圆角
//                    if (params.animateRadius) {
//                        chatMessageCell.getPhotoImage().setRoundRadius(
//                                (int) (finalFromRoundRadius[0] * (1f - v) + params.animateToRadius[0] * v),
//                                (int) (finalFromRoundRadius[1] * (1f - v) + params.animateToRadius[1] * v),
//                                (int) (finalFromRoundRadius[2] * (1f - v) + params.animateToRadius[2] * v),
//                                (int) (finalFromRoundRadius[3] * (1f - v) + params.animateToRadius[3] * v)
//                        );
//                    }
//
//                    // 设置图片坐标
//                    chatMessageCell.setImageCoords(x, y, width, height);
//                    // 刷新视图
//                    holder.itemView.invalidate();
//                });
//                // 添加到动画集合
//                animatorSet.playTogether(valueAnimator);
//            }
//            if (moveInfoExtended.deltaBottom != 0 || moveInfoExtended.deltaRight != 0 || moveInfoExtended.deltaTop != 0 || moveInfoExtended.deltaLeft != 0) {
//
//                recyclerListView.setClipChildren(false);
//                recyclerListView.invalidate();
//
//                ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0);
//                if (moveInfoExtended.animateBackgroundOnly) {
//                    params.toDeltaLeft = -moveInfoExtended.deltaLeft;
//                    params.toDeltaRight = -moveInfoExtended.deltaRight;
//                } else {
//                    params.toDeltaLeft = -moveInfoExtended.deltaLeft - chatMessageCell.getAnimationOffsetX();
//                    params.toDeltaRight = -moveInfoExtended.deltaRight - chatMessageCell.getAnimationOffsetX();
//                }
//                valueAnimator.addUpdateListener(animation -> {
//                    float v = (float) animation.getAnimatedValue();
//                    if (moveInfoExtended.animateBackgroundOnly) {
//                        params.deltaLeft = -moveInfoExtended.deltaLeft * v;
//                        params.deltaRight = -moveInfoExtended.deltaRight * v;
//                        params.deltaTop = -moveInfoExtended.deltaTop * v;
//                        params.deltaBottom = -moveInfoExtended.deltaBottom * v;
//                    } else {
//                        params.deltaLeft = -moveInfoExtended.deltaLeft * v - chatMessageCell.getAnimationOffsetX();
//                        params.deltaRight = -moveInfoExtended.deltaRight * v - chatMessageCell.getAnimationOffsetX();
//                        params.deltaTop = -moveInfoExtended.deltaTop * v - chatMessageCell.getTranslationY();
//                        params.deltaBottom = -moveInfoExtended.deltaBottom * v - chatMessageCell.getTranslationY();
//                    }
//                    chatMessageCell.invalidate();
//                });
//                animatorSet.playTogether(valueAnimator);
//            } else {
//                params.toDeltaLeft = 0;
//                params.toDeltaRight = 0;
//            }
//
////            MessageObject.GroupedMessages group = chatMessageCell.getCurrentMessagesGroup();
////            if (group == null) {
////                moveInfoExtended.animateChangeGroupBackground = false;
////            }
//
////            if (moveInfoExtended.animateChangeGroupBackground) {
////                ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0);
////                MessageObject.GroupedMessages.TransitionParams groupTransitionParams = group.transitionParams;
////                RecyclerListView recyclerListView = (RecyclerListView) holder.itemView.getParent();
////
////                float captionEnterFrom = group.transitionParams.captionEnterProgress;
////                float captionEnterTo = group.hasCaption ? 1 : 0;
////
////                boolean animateCaption = captionEnterFrom != captionEnterTo;
////                valueAnimator.addUpdateListener(animation -> {
////                    float v = (float) animation.getAnimatedValue();
////                    groupTransitionParams.offsetTop = moveInfoExtended.groupOffsetTop * v;
////                    groupTransitionParams.offsetBottom = moveInfoExtended.groupOffsetBottom * v;
////                    groupTransitionParams.offsetLeft = moveInfoExtended.groupOffsetLeft * v;
////                    groupTransitionParams.offsetRight = moveInfoExtended.groupOffsetRight * v;
////                    if (animateCaption) {
////                        groupTransitionParams.captionEnterProgress = captionEnterFrom * v + captionEnterTo * (1f - v);
////                    }
////                    if (recyclerListView != null) {
////                        recyclerListView.invalidate();
////                    }
////                });
////
////                valueAnimator.addListener(new AnimatorListenerAdapter() {
////                    @Override
////                    public void onAnimationEnd(Animator animation) {
////                        groupTransitionParams.backgroundChangeBounds = false;
////                        groupTransitionParams.drawBackgroundForDeletedItems = false;
////                    }
////                });
////                animatorSet.playTogether(valueAnimator);
////            }
//
//            if (moveInfoExtended.animatePinnedBottom) {
//                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
//                valueAnimator.addUpdateListener(animation -> {
//                    params.changePinnedBottomProgress = (float) animation.getAnimatedValue();
//                    chatMessageCell.invalidate();
//                });
//
//                animatorSet.playTogether(valueAnimator);
//            }
//
//            if (moveInfoExtended.animateChangeInternal) {
//                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
//                params.animateChange = true;
//                valueAnimator.addUpdateListener(animation -> {
//                    params.animateChangeProgress = (float) animation.getAnimatedValue();
//                    chatMessageCell.invalidate();
//                });
//                animatorSet.playTogether(valueAnimator);
//            }
//        }
//
//        if (withThanos) {
//            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
//        } else if (translationInterpolator != null) {
//            animatorSet.setInterpolator(translationInterpolator);
//        }
//        animatorSet.setDuration((long) (getMoveDuration() * (withThanos ? 1.9f : 1f)));
//        animatorSet.addListener(new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationStart(Animator animator) {
//                dispatchMoveStarting(holder);
//            }
//
//            @Override
//            public void onAnimationCancel(Animator animator) {
//                if (deltaY != 0) {
//                    view.setTranslationY(0);
//                }
//            }
//
//            @Override
//            public void onAnimationEnd(Animator animator) {
//                animator.removeAllListeners();
//                restoreTransitionParams(holder.itemView);
//                if (holder.itemView instanceof ChatMessageCell) {
//                    ChatMessageCell cell = (ChatMessageCell) holder.itemView;
//                    if (cell.makeVisibleAfterChange) {
//                        cell.makeVisibleAfterChange = false;
//                        cell.setVisibility(View.VISIBLE);
//                    }
//                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
//                    if (group != null) {
//                        group.transitionParams.reset();
//                    }
//                }
//                if (mMoveAnimations.remove(holder)) {
//                    dispatchMoveFinished(holder);
//                    dispatchFinishedWhenDone();
//                }
//            }
//        });
//        animatorSet.start();
//        animators.put(holder, animatorSet);
//    }
//
//    @Override
//    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, ItemHolderInfo info,
//                                 int fromX, int fromY, int toX, int toY) {
//        if (oldHolder == newHolder) {
//            // Don't know how to run change animations when the same view holder is re-used.
//            // run a move animation to handle position changes.
//            return animateMove(oldHolder, info, fromX, fromY, toX, toY);
//        }
//        final float prevTranslationX;
//        if (oldHolder.itemView instanceof ChatMessageCell) {
//            prevTranslationX = ((ChatMessageCell) oldHolder.itemView).getAnimationOffsetX();
//        } else {
//            prevTranslationX = oldHolder.itemView.getTranslationX();
//        }
//        final float prevTranslationY = oldHolder.itemView.getTranslationY();
//        final float prevAlpha = oldHolder.itemView.getAlpha();
//        resetAnimation(oldHolder);
//        int deltaX = (int) (toX - fromX - prevTranslationX);
//        int deltaY = (int) (toY - fromY - prevTranslationY);
//        // recover prev translation state after ending animation
//        if (oldHolder.itemView instanceof ChatMessageCell) {
//            ((ChatMessageCell) oldHolder.itemView).setAnimationOffsetX(prevTranslationX);
//        } else {
//            oldHolder.itemView.setTranslationX(prevTranslationX);
//        }
//        oldHolder.itemView.setTranslationY(prevTranslationY);
//        oldHolder.itemView.setAlpha(prevAlpha);
//        if (newHolder != null) {
//            // carry over translation values
//            resetAnimation(newHolder);
//            if (newHolder.itemView instanceof ChatMessageCell) {
//                ((ChatMessageCell) newHolder.itemView).setAnimationOffsetX(-deltaX);
//            } else {
//                newHolder.itemView.setTranslationX(-deltaX);
//            }
//            newHolder.itemView.setTranslationY(-deltaY);
//            newHolder.itemView.setAlpha(0);
//        }
//        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
//        checkIsRunning();
//        return true;
//    }
//
//    // 动画改变实现
//    public void animateChangeImpl(final ChangeInfo changeInfo) {
//        // 获取旧持有者
//        final RecyclerView.ViewHolder holder = changeInfo.oldHolder;
//        // 获取旧视图
//        final View view = holder == null ? null : holder.itemView;
//        // 获取新持有者
//        final RecyclerView.ViewHolder newHolder = changeInfo.newHolder;
//        // 获取新视图
//        final View newView = newHolder != null ? newHolder.itemView : null;
//        // 如果旧视图不为空
//        if (view != null) {
//            // 获取 ViewPropertyAnimator
//            final ViewPropertyAnimator oldViewAnim = view.animate().setDuration(
//                    getChangeDuration());
//            // 添加到添加动画列表
//            mChangeAnimations.add(changeInfo.oldHolder);
//            // 设置平移 X
//            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
//            // 设置平移 Y
//            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
//            // 设置 Alpha 为 0 并设置监听器
//            oldViewAnim.alpha(0).setListener(new AnimatorListenerAdapter() {
//                @Override
//                // 动画开始
//                public void onAnimationStart(Animator animator) {
//                    dispatchChangeStarting(changeInfo.oldHolder, true);
//                }
//
//                @Override
//                // 动画结束
//                public void onAnimationEnd(Animator animator) {
//                    // 清除监听器
//                    oldViewAnim.setListener(null);
//                    // 设置 Alpha 为 1
//                    view.setAlpha(1);
//                    // 设置缩放为 1
//                    view.setScaleX(1f);
//                    view.setScaleX(1f);
//                    // 如果是消息单元格
//                    if (view instanceof ChatMessageCell) {
//                        // 重置动画 X 偏移
//                        ((ChatMessageCell) view).setAnimationOffsetX(0);
//                    } else {
//                        // 重置 X 平移
//                        view.setTranslationX(0);
//                    }
//                    // 重置 Y 平移
//                    view.setTranslationY(0);
//                    // 从移除动画列表中移除并分发结束
//                    if (mChangeAnimations.remove(changeInfo.oldHolder)) {
//                        dispatchChangeFinished(changeInfo.oldHolder, true);
//                        dispatchFinishedWhenDone();
//                    }
//                }
//            }).start();
//        }
//        // 如果新视图不为空
//        if (newView != null) {
//            // 获取 ViewPropertyAnimator
//            final ViewPropertyAnimator newViewAnimation = newView.animate();
//            // 添加到添加动画列表
//            mChangeAnimations.add(changeInfo.newHolder);
//            // 设置平移回 0，设置 Alpha 为 1，并设置监听器
//            newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration())
//                    .alpha(1).setListener(new AnimatorListenerAdapter() {
//                @Override
//                // 动画开始
//                public void onAnimationStart(Animator animator) {
//                    dispatchChangeStarting(changeInfo.newHolder, false);
//                }
//
//                @Override
//                public void onAnimationEnd(Animator animator) {
//                    newViewAnimation.setListener(null);
//                    newView.setAlpha(1);
//                    newView.setScaleX(1f);
//                    newView.setScaleX(1f);
//                    if (newView instanceof ChatMessageCell) {
//                        ((ChatMessageCell) newView).setAnimationOffsetX(0);
//                    } else {
//                        newView.setTranslationX(0);
//                    }
//                    newView.setTranslationY(0);
//
//                    if (mChangeAnimations.remove(changeInfo.newHolder)) {
//                        dispatchChangeFinished(changeInfo.newHolder, false);
//                        dispatchFinishedWhenDone();
//                    }
//                }
//            }).start();
//        }
//    }
//
//    @NonNull
//    @Override
//    // 记录布局前信息
//    public ItemHolderInfo recordPreLayoutInformation(@NonNull RecyclerView.State state, @NonNull RecyclerView.ViewHolder viewHolder, int changeFlags, @NonNull List<Object> payloads) {
//        // 调用父类方法
//        ItemHolderInfo info = super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads);
//        // 如果是消息单元格
//        if (viewHolder.itemView instanceof ChatMessageCell) {
//            ChatMessageCell chatMessageCell = (ChatMessageCell) viewHolder.itemView;
//            // 创建扩展信息对象
//            ItemHolderInfoExtended extended = new ItemHolderInfoExtended();
//            // 复制基本信息
//            extended.left = info.left;
//            extended.top = info.top;
//            extended.right = info.right;
//            extended.bottom = info.bottom;
//
//            // 获取过渡参数
//            ChatMessageCell.TransitionParams params = chatMessageCell.getTransitionParams();
//            // 记录图片位置和尺寸
//            extended.imageX = params.lastDrawingImageX;
//            extended.imageY = params.lastDrawingImageY;
//            extended.imageWidth = params.lastDrawingImageW;
//            extended.imageHeight = params.lastDrawingImageH;
//            return extended;
//        }
//        return info;
//    }
//
//    @Override
//    // 所有动画结束时调用
//    protected void onAllAnimationsDone() {
//        super.onAllAnimationsDone();
//
//        // 恢复裁剪子视图
//        recyclerListView.setClipChildren(true);
//        // 运行所有动画结束时的回调
//        while (!runOnAnimationsEnd.isEmpty()) {
//            runOnAnimationsEnd.remove(0).run();
//        }
//        // 取消所有动画器
//        cancelAnimators();
//    }
//
//    // 取消动画器
//    private void cancelAnimators() {
//        // 复制动画器列表
//        ArrayList<Animator> anim = new ArrayList<>(animators.values());
//        // 清空映射
//        animators.clear();
//        // 遍历并取消动画
//        for (Animator animator : anim) {
//            if (animator != null) {
//                animator.cancel();
//            }
//        }
//        // 如果有灭霸效果视图
//        if (!thanosViews.isEmpty()) {
//            // 获取灭霸效果
//            ThanosEffect thanosEffect = getThanosEffectContainer.run();
//            if (thanosEffect != null) {
//                // 终止效果
//                thanosEffect.kill();
//            }
//        }
//    }
//
//    @Override
//    // 结束单个项目的动画
//    public void endAnimation(RecyclerView.ViewHolder item) {
//        // 从映射中移除动画器
//        Animator animator = animators.remove(item);
//        if (animator != null) {
//            animator.cancel();
//        }
//        // 如果在灭霸效果视图中
//        if (thanosViews.contains(item.itemView)) {
//            // 获取灭霸效果
//            ThanosEffect thanosEffect = getThanosEffectContainer.run();
//            if (thanosEffect != null) {
//                // 取消该视图的效果
//                thanosEffect.cancel(item.itemView);
//            }
//        }
//        // 调用父类方法
//        super.endAnimation(item);
//        // 恢复过渡参数
//        restoreTransitionParams(item.itemView);
//    }
//
//    // 恢复过渡参数
//    private void restoreTransitionParams(View view) {
//        // 重置 Alpha
//        view.setAlpha(1f);
//        // 重置缩放
//        view.setScaleX(1f);
//        view.setScaleY(1f);
//        // 重置 Y 平移
//        view.setTranslationY(0f);
//        // 如果是机器人帮助单元格
////        if (view instanceof BotHelpCell) {
////            BotHelpCell botCell = (BotHelpCell) view;
////            // 计算顶部位置
////            int top = recyclerListView.getMeasuredHeight() / 2 - view.getMeasuredHeight() / 2;
////            // 停止动画
////            botCell.setAnimating(false);
////            // 如果在顶部下方
////            if (view.getTop() > top) {
////                // 设置平移
////                view.setTranslationY(top - view.getTop());
////            } else {
////                // 重置平移
////                view.setTranslationY(0);
////            }
////        } else if (view instanceof UserInfoCell) {
////            // 如果是用户信息单元格
////            UserInfoCell cell = (UserInfoCell) view;
////            // 计算顶部位置
////            int top = recyclerListView.getMeasuredHeight() / 2 - view.getMeasuredHeight() / 2;
////            // 停止动画
////            cell.setAnimating(false);
////            // 如果在顶部下方
////            if (view.getTop() > top) {
////                // 设置平移
////                view.setTranslationY(top - view.getTop());
////            } else {
////                // 重置平移
////                view.setTranslationY(0);
////            }
////        } else
//
//            if (view instanceof ChatMessageCell) {
//            // 如果是消息单元格，重置动画
//            ((ChatMessageCell) view).getTransitionParams().resetAnimation();
//            ((ChatMessageCell) view).setAnimationOffsetX(0f);
//        }
////            else if (view instanceof ChatActionCell) {
////            // 如果是聊天动作单元格，重置动画
////            ((ChatActionCell) view).getTransitionParams().resetAnimation();
////        }
//
//            else {
//            // 其他情况重置 X 平移
//            view.setTranslationX(0f);
//        }
//    }
//
//    @Override
//    // 结束所有动画
//    public void endAnimations() {
//        // 重置所有将要改变的分组状态
//        for (MessageObject.GroupedMessages groupedMessages : willChangedGroups) {
//            groupedMessages.transitionParams.isNewGroup = false;
//        }
//        willChangedGroups.clear();
//        // 取消所有动画器
//        cancelAnimators();
//
//        // 恢复问候视图状态
//        if (chatGreetingsView != null) {
//            chatGreetingsView.stickerToSendView.setAlpha(1f);
//        }
//        greetingsSticker = null;
//        chatGreetingsView = null;
//
//        // 恢复挂起的移动动画
//        int count = mPendingMoves.size();
//        for (int i = count - 1; i >= 0; i--) {
//            MoveInfo item = mPendingMoves.get(i);
//            View view = item.holder.itemView;
//            restoreTransitionParams(view);
//            dispatchMoveFinished(item.holder);
//            mPendingMoves.remove(i);
//        }
//        // 恢复挂起的移除动画
//        count = mPendingRemovals.size();
//        for (int i = count - 1; i >= 0; i--) {
//            RecyclerView.ViewHolder item = mPendingRemovals.get(i);
//            restoreTransitionParams(item.itemView);
//            dispatchRemoveFinished(item);
//            mPendingRemovals.remove(i);
//        }
//        // 恢复挂起的添加动画
//        count = mPendingAdditions.size();
//        for (int i = count - 1; i >= 0; i--) {
//            RecyclerView.ViewHolder item = mPendingAdditions.get(i);
//            restoreTransitionParams(item.itemView);
//            dispatchAddFinished(item);
//            mPendingAdditions.remove(i);
//        }
//        // 恢复挂起的改变动画
//        count = mPendingChanges.size();
//        for (int i = count - 1; i >= 0; i--) {
//            endChangeAnimationIfNecessary(mPendingChanges.get(i));
//        }
//        mPendingChanges.clear();
//        // 如果没有运行中的动画，直接返回
//        if (!isRunning()) {
//            return;
//        }
//
//        // 清理移动列表
//        int listCount = mMovesList.size();
//        for (int i = listCount - 1; i >= 0; i--) {
//            ArrayList<MoveInfo> moves = mMovesList.get(i);
//            count = moves.size();
//            for (int j = count - 1; j >= 0; j--) {
//                MoveInfo moveInfo = moves.get(j);
//                RecyclerView.ViewHolder item = moveInfo.holder;
//                restoreTransitionParams(item.itemView);
//                dispatchMoveFinished(moveInfo.holder);
//                moves.remove(j);
//                if (moves.isEmpty()) {
//                    mMovesList.remove(moves);
//                }
//            }
//        }
//        // 清理添加列表
//        listCount = mAdditionsList.size();
//        for (int i = listCount - 1; i >= 0; i--) {
//            ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
//            count = additions.size();
//            for (int j = count - 1; j >= 0; j--) {
//                RecyclerView.ViewHolder item = additions.get(j);
//                restoreTransitionParams(item.itemView);
//                dispatchAddFinished(item);
//                additions.remove(j);
//                if (additions.isEmpty()) {
//                    mAdditionsList.remove(additions);
//                }
//            }
//        }
//        // 清理改变列表
//        listCount = mChangesList.size();
//        for (int i = listCount - 1; i >= 0; i--) {
//            ArrayList<ChangeInfo> changes = mChangesList.get(i);
//            count = changes.size();
//            for (int j = count - 1; j >= 0; j--) {
//                endChangeAnimationIfNecessary(changes.get(j));
//                if (changes.isEmpty()) {
//                    mChangesList.remove(changes);
//                }
//            }
//        }
//        // 取消所有列表中的动画
//        cancelAll(mRemoveAnimations);
//        cancelAll(mMoveAnimations);
//        cancelAll(mAddAnimations);
//        cancelAll(mChangeAnimations);
//
//        // 分发动画全部结束
//        dispatchAnimationsFinished();
//    }
//
//    // 必要时结束改变动画
//    protected boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, RecyclerView.ViewHolder item) {
//        Animator a = animators.remove(item);
//        if (a != null) {
//            a.cancel();
//        }
//        if (thanosViews.contains(item.itemView)) {
//            ThanosEffect thanosEffect = getThanosEffectContainer.run();
//            if (thanosEffect != null) {
//                thanosEffect.cancel(item.itemView);
//            }
//        }
//
//        boolean oldItem = false;
//        if (changeInfo.newHolder == item) {
//            changeInfo.newHolder = null;
//        } else if (changeInfo.oldHolder == item) {
//            changeInfo.oldHolder = null;
//            oldItem = true;
//        } else {
//            return false;
//        }
//        restoreTransitionParams(item.itemView);
//        dispatchChangeFinished(item, oldItem);
//
//        return true;
//    }
//
//    // 分组将转换为单条消息
//    public void groupWillTransformToSingleMessage(MessageObject.GroupedMessages groupedMessages) {
//        willRemovedGroup.put(groupedMessages.messages.get(0).getId(), groupedMessages);
//    }
//
//    // 分组将要改变
////    public void groupWillChanged(MessageObject.GroupedMessages groupedMessages) {
////        if (groupedMessages == null) {
////            return;
////        }
////        if (groupedMessages.messages.size() == 0) {
////            groupedMessages.transitionParams.drawBackgroundForDeletedItems = true;
////        } else {
////            // 如果边界都为 0，尝试查找边界
////            if (groupedMessages.transitionParams.top == 0 && groupedMessages.transitionParams.bottom == 0 && groupedMessages.transitionParams.left == 0 && groupedMessages.transitionParams.right == 0)  {
////                int n = recyclerListView.getChildCount();
////                for (int i = 0; i < n; i++) {
////                    View child = recyclerListView.getChildAt(i);
////                    if (child instanceof ChatMessageCell) {
////                        ChatMessageCell cell = (ChatMessageCell) child;
////                        MessageObject messageObject = cell.getMessageObject();
////                        // 如果之前绘制过且属于该分组
////                        if (cell.getTransitionParams().wasDraw && groupedMessages.messages.contains(messageObject)) {
////                            // 设置边界和状态
////                            groupedMessages.transitionParams.top = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableTop();
////                            groupedMessages.transitionParams.bottom = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom();
////                            groupedMessages.transitionParams.left = cell.getLeft() + cell.getBackgroundDrawableLeft();
////                            groupedMessages.transitionParams.right = cell.getLeft() + cell.getBackgroundDrawableRight();
////                            groupedMessages.transitionParams.drawCaptionLayout = cell.hasCaptionLayout();
////                            groupedMessages.transitionParams.pinnedTop = cell.isPinnedTop();
////                            groupedMessages.transitionParams.pinnedBotton = cell.isPinnedBottom();
////                            groupedMessages.transitionParams.isNewGroup = true;
////                            break;
////                        }
////                    }
////                }
////            }
////            // 添加到将要改变的分组列表
////            willChangedGroups.add(groupedMessages);
////        }
////    }
//
//    @Override
//    // 动画添加实现
//    public void animateAddImpl(RecyclerView.ViewHolder holder) {
//        final View view = holder.itemView;
//        mAddAnimations.add(holder);
//        // 如果是问候贴纸
//        if (holder == greetingsSticker) {
//            view.setAlpha(1f);
//        }
//        AnimatorSet animatorSet = new AnimatorSet();
//
//        if (view instanceof ChatMessageCell) {
//            ChatMessageCell cell = (ChatMessageCell) view;
//            // 如果有动画 X 偏移
//            if (cell.getAnimationOffsetX() != 0) {
//                animatorSet.playTogether(
//                        ObjectAnimator.ofFloat(cell, cell.ANIMATION_OFFSET_X, cell.getAnimationOffsetX(), 0f)
//                );
//            }
//            float pivotX = cell.getBackgroundDrawableLeft() + (cell.getBackgroundDrawableRight() - cell.getBackgroundDrawableLeft()) / 2f;
//            // 设置中心 X
//            cell.setPivotX(pivotX);
//            // 动画 Y 平移回 0
//            view.animate().translationY(0).setDuration(getAddDuration()).start();
//        } else {
//            // 动画 X, Y 平移回 0
//            view.animate().translationX(0).translationY(0).setDuration(getAddDuration()).start();
//        }
//
//        // 是否使用缩放
//        boolean useScale = true;
//        // 计算当前延迟
//        long currentDelay = (long) ((1f - Math.max(0, Math.min(1f, view.getBottom() / (float) recyclerListView.getMeasuredHeight()))) * 100);
//
//        // 如果是消息单元格
//        if (view instanceof ChatMessageCell){
//            // 如果是问候贴纸
////            if (holder == greetingsSticker) {
////                // 不使用缩放
////                useScale = false;
////                // 隐藏问候视图的贴纸
////                if (chatGreetingsView != null) {
////                    chatGreetingsView.stickerToSendView.setAlpha(0f);
////                }
////                // 允许子视图绘制到边界外
////                recyclerListView.setClipChildren(false);
////                ChatMessageCell messageCell = (ChatMessageCell) view;
////                View parentForGreetingsView = (View)chatGreetingsView.getParent();
////                // 计算起始和目标位置
////                float fromX = chatGreetingsView.stickerToSendView.getX() + chatGreetingsView.getX() + parentForGreetingsView.getX();
////                float fromY = chatGreetingsView.stickerToSendView.getY() + chatGreetingsView.getY() + parentForGreetingsView.getY();
////                float toX = messageCell.getPhotoImage().getImageX() + recyclerListView.getX() + messageCell.getX();
////                float toY = messageCell.getPhotoImage().getImageY() + recyclerListView.getY() + messageCell.getY();
////                float fromW = chatGreetingsView.stickerToSendView.getWidth();
////                float fromH = chatGreetingsView.stickerToSendView.getHeight();
////                float toW = messageCell.getPhotoImage().getImageWidth();
////                float toH = messageCell.getPhotoImage().getImageHeight();
////                float deltaX = fromX - toX;
////                float deltaY = fromY - toY;
////
////                toX = messageCell.getPhotoImage().getImageX();
////                toY = messageCell.getPhotoImage().getImageY();
////
////                // 设置图片边界过渡
////                messageCell.getTransitionParams().imageChangeBoundsTransition = true;
////                // 设置动画绘制时间 Alpha
////                messageCell.getTransitionParams().animateDrawingTimeAlpha = true;
////                // 设置图片坐标
////                messageCell.getPhotoImage().setImageCoords(toX + deltaX, toX + deltaY, fromW,fromH);
////
////                // 创建 ValueAnimator
////                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0,1f);
////                float finalToX = toX;
////                float finalToY = toY;
////                // 添加更新监听器
////                valueAnimator.addUpdateListener(animation -> {
////                    // 获取动画进度
////                    float v = (float) animation.getAnimatedValue();
////                    // 更新动画改变进度
////                    messageCell.getTransitionParams().animateChangeProgress = v;
////                    if (messageCell.getTransitionParams().animateChangeProgress > 1) {
////                        messageCell.getTransitionParams().animateChangeProgress = 1f;
////                    }
////                    // 更新图片坐标
////                    messageCell.getPhotoImage().setImageCoords(
////                            finalToX + deltaX * (1f - v),
////                            finalToY + deltaY * (1f - v),
////                            fromW * (1f - v) + toW * v,
////                            fromH * (1f - v) + toH * v);
////                    // 刷新单元格
////                    messageCell.invalidate();
////                });
////                // 添加监听器
////                valueAnimator.addListener(new AnimatorListenerAdapter() {
////                    @Override
////                    // 动画结束
////                    public void onAnimationEnd(Animator animation) {
////                        // 重置动画
////                        messageCell.getTransitionParams().resetAnimation();
////                        // 设置图片坐标
////                        messageCell.getPhotoImage().setImageCoords(finalToX, finalToY, toW, toH);
////                        // 恢复问候视图贴纸 Alpha
////                        if (chatGreetingsView != null) {
////                            chatGreetingsView.stickerToSendView.setAlpha(1f);
////                        }
////                        // 刷新单元格
////                        messageCell.invalidate();
////                    }
////                });
////                // 添加到动画集合
////                animatorSet.play(valueAnimator);
////            }
//
////            else {
//                // 获取当前消息分组
////                MessageObject.GroupedMessages groupedMessages = ((ChatMessageCell) view).getCurrentMessagesGroup();
////                if (groupedMessages != null) {
////                    // 获取分组延迟
////                    Long groupDelay = groupIdToEnterDelay.get(groupedMessages.groupId);
////                    if (groupDelay == null) {
////                        // 如果没有，存入当前延迟
////                        groupIdToEnterDelay.put(groupedMessages.groupId, currentDelay);
////                    } else {
////                        // 如果有，使用分组延迟
////                        currentDelay = groupDelay;
////                    }
////                }
//                // 如果有分组且背景改变边界
////                if (groupedMessages != null && groupedMessages.transitionParams.backgroundChangeBounds) {
////                    // 设置启动延迟
////                    animatorSet.setStartDelay(140);
////                }
//            }
//        }
//
//        // 设置 Alpha 为 0
//        view.setAlpha(0f);
//        // 添加 Alpha 动画
//        animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1f));
//        // 如果使用缩放
//        if (useScale) {
//            // 设置初始缩放
//            view.setScaleX(0.9f);
//            view.setScaleY(0.9f);
//            // 添加缩放动画
//            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleY(), 1f));
//            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), 1f));
//        } else {
//            // 设置缩放为 1
//            view.setScaleX(1f);
//            view.setScaleY(1f);
//        }
//
//        // 如果是问候贴纸
//        if (holder == greetingsSticker) {
//            // 设置持续时间
//            animatorSet.setDuration(350);
//            // 设置插值器
//            animatorSet.setInterpolator(new OvershootInterpolator());
//        } else {
//            // 设置启动延迟
//            animatorSet.setStartDelay(currentDelay);
//            // 设置持续时间
//            animatorSet.setDuration(DEFAULT_DURATION);
//        }
//
//        // 添加监听器
//        animatorSet.addListener(new AnimatorListenerAdapter() {
//            @Override
//            // 动画开始
//            public void onAnimationStart(Animator animator) {
//                dispatchAddStarting(holder);
//            }
//
//            @Override
//            // 动画取消
//            public void onAnimationCancel(Animator animator) {
//                view.setAlpha(1);
//            }
//
//            @Override
//            // 动画结束
//            public void onAnimationEnd(Animator animator) {
//                // 清除监听器
//                animator.removeAllListeners();
//                // 设置属性
//                view.setAlpha(1f);
//                view.setScaleX(1f);
//                view.setScaleY(1f);
//                view.setTranslationY(0f);
//                view.setTranslationY(0f);
//                // 从添加动画列表中移除并分发结束
//                if (mAddAnimations.remove(holder)) {
//                    dispatchAddFinished(holder);
//                    dispatchFinishedWhenDone();
//                }
//            }
//        });
//        // 将动画对象放入映射中
//        animators.put(holder, animatorSet);
//        // 开始动画
//        animatorSet.start();
//    }
//
//    // 动画移除实现
//    protected void animateRemoveImpl(final RecyclerView.ViewHolder holder) {
//        animateRemoveImpl(holder, false);
//    }
//    // 动画移除实现（带灭霸效果参数）
//    protected void animateRemoveImpl(final RecyclerView.ViewHolder holder, boolean thanos) {
//        final View view = holder.itemView;
//        mRemoveAnimations.add(holder);
//        // 如果有灭霸效果
//        if (thanos && getThanosEffectContainer != null) {
//            // 获取灭霸效果
//            ThanosEffect thanosEffect = getThanosEffectContainer.run();
//            // 分发移除开始
//            dispatchRemoveStarting(holder);
//            // 执行灭霸动画
//            thanosEffect.animate(view, () -> {
//                // 设置可见
//                view.setVisibility(View.VISIBLE);
//                // 从移除动画列表中移除并分发结束
//                if (mRemoveAnimations.remove(holder)) {
//                    dispatchRemoveFinished(holder);
//                    dispatchFinishedWhenDone();
//                }
//                // 从灭霸视图列表中移除
//                thanosViews.remove(view);
//            });
//            // 添加到灭霸视图列表
//            thanosViews.add(view);
//        } else {
//            // 创建 Alpha 动画
//            ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f);
//            // 分发移除开始
//            dispatchRemoveStarting(holder);
//            // 设置持续时间
//            animator.setDuration(getRemoveDuration());
//            // 添加监听器
//            animator.addListener(
//                    new AnimatorListenerAdapter() {
//                        @Override
//                        // 动画结束
//                        public void onAnimationEnd(Animator animator) {
//                            // 清除监听器
//                            animator.removeAllListeners();
//                            // 重置属性
//                            view.setAlpha(1);
//                            view.setScaleX(1f);
//                            view.setScaleY(1f);
//                            view.setTranslationX(0);
//                            view.setTranslationY(0);
//                            // 从移除动画列表中移除并分发结束
//                            if (mRemoveAnimations.remove(holder)) {
//                                dispatchRemoveFinished(holder);
//                                dispatchFinishedWhenDone();
//                            }
//                        }
//                    });
//            // 将动画对象放入映射中
//            animators.put(holder, animator);
//            // 开始动画
//            animator.start();
//        }
//        // 停止滚动
//        recyclerListView.stopScroll();
//    }
//
//    // 动画移除分组实现
//    private void animateRemoveGroupImpl(final ArrayList<RecyclerView.ViewHolder> holders) {
//        // 添加到移除动画列表
//        mRemoveAnimations.addAll(holders);
//        // 获取灭霸效果
//        ThanosEffect thanosEffect = getThanosEffectContainer.run();
//        // 分发移除开始
//        for (int i = 0; i < holders.size(); ++i) {
//            dispatchRemoveStarting(holders.get(i));
//        }
//        // 创建视图列表
//        final ArrayList<View> views = new ArrayList<>();
//        for (int i = 0; i < holders.size(); ++i) {
//            views.add(holders.get(i).itemView);
//        }
//        // 执行分组灭霸动画
//        thanosEffect.animateGroup(views, () -> {
//            // 设置所有视图可见
//            for (int i = 0; i < views.size(); ++i) {
//                views.get(i).setVisibility(View.VISIBLE);
//            }
//            // 从移除动画列表中移除并分发结束
//            if (mRemoveAnimations.removeAll(holders)) {
//                for (int i = 0; i < holders.size(); ++i) {
//                    dispatchRemoveFinished(holders.get(i));
//                }
//                dispatchFinishedWhenDone();
//            }
//            // 从灭霸视图列表中移除
//            thanosViews.removeAll(views);
//        });
//        // 添加第一个视图到灭霸视图列表（作为标记？）
//        thanosViews.add(views.get(0));
//        // 停止滚动
//        recyclerListView.stopScroll();
//    }
//
//    // 设置是否应该从底部动画进入
//    public void setShouldAnimateEnterFromBottom(boolean shouldAnimateEnterFromBottom) {
//        this.shouldAnimateEnterFromBottom = shouldAnimateEnterFromBottom;
//    }
//
//    // 动画开始回调
//    public void onAnimationStart() {
//
//    }
//
//    // 获取移动动画延迟
//    protected long getMoveAnimationDelay() {
//        return 0;
//    }
//
//    @Override
//    // 获取移动持续时间
//    public long getMoveDuration() {
//        return DEFAULT_DURATION;
//    }
//
//    @Override
//    // 获取改变持续时间
//    public long getChangeDuration() {
//        return DEFAULT_DURATION;
//    }
//
//    // 运行动画结束后的任务
//    public void runOnAnimationEnd(Runnable runnable) {
//        runOnAnimationsEnd.add(runnable);
//    }
//
//    public void onDestroy() {
//        onAllAnimationsDone();
//    }
//
//    public boolean willRemoved(View view) {
//        RecyclerView.ViewHolder holder = recyclerListView.getChildViewHolder(view);
//        if (holder != null) {
//            return mPendingRemovals.contains(holder) || mRemoveAnimations.contains(holder);
//        }
//        return false;
//    }
//
//    public boolean willAddedFromAlpha(View view) {
//        if (shouldAnimateEnterFromBottom) {
//            return false;
//        }
//        RecyclerView.ViewHolder holder = recyclerListView.getChildViewHolder(view);
//        if (holder != null) {
//            return mPendingAdditions.contains(holder) || mAddAnimations.contains(holder);
//        }
//        return false;
//    }
//
//    public void onGreetingStickerTransition(RecyclerView.ViewHolder holder, ChatGreetingsView greetingsViewContainer) {
//        greetingsSticker = holder;
//        chatGreetingsView = greetingsViewContainer;
//        shouldAnimateEnterFromBottom = false;
//    }
//
//    public void setReversePositions(boolean reversePositions) {
//        this.reversePositions = reversePositions;
//    }
//
//    class MoveInfoExtended extends DefaultItemAnimator.MoveInfo {
//
//        public float captionDeltaX;
//        public float captionDeltaY;
//
//        public int groupOffsetTop;
//        public int groupOffsetBottom;
//        public int groupOffsetLeft;
//        public int groupOffsetRight;
//        public boolean animateChangeGroupBackground;
//        public boolean animatePinnedBottom;
//        public boolean animateBackgroundOnly;
//        public boolean animateChangeInternal;
//
//        boolean animateImage;
//        boolean drawBackground;
//
//        float imageX;
//        float imageY;
//        float imageWidth;
//        float imageHeight;
//
//        int deltaLeft, deltaRight, deltaTop, deltaBottom;
//        boolean animateRemoveGroup;
//
//        MoveInfoExtended(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
//            super(holder, fromX, fromY, toX, toY);
//        }
//    }
//
//    class ItemHolderInfoExtended extends RecyclerView.ItemAnimator.ItemHolderInfo {
//        float imageX;
//        float imageY;
//        float imageWidth;
//        float imageHeight;
//        int captionX;
//        int captionY;
//    }
//
//    private final ArrayList<RecyclerView.ViewHolder> toBeSnapped = new ArrayList<>();
//    public void prepareThanos(RecyclerView.ViewHolder viewHolder) {
//        if (viewHolder == null) return;
//        toBeSnapped.add(viewHolder);
//        if (viewHolder.itemView instanceof ChatMessageCell) {
//            MessageObject msg = ((ChatMessageCell) viewHolder.itemView).getMessageObject();
//            if (msg != null) {
//                msg.deletedByThanos = true;
//            }
//        }
//    }
//
//    private Utilities.Callback0Return<Boolean> supportsThanosEffectContainer;
//    private Utilities.Callback0Return<ThanosEffect> getThanosEffectContainer;
//    public void setOnSnapMessage(
//        Utilities.Callback0Return<Boolean> supportsThanosEffectContainer,
//        Utilities.Callback0Return<ThanosEffect> getThanosEffectContainer
//    ) {
//        this.supportsThanosEffectContainer = supportsThanosEffectContainer;
//        this.getThanosEffectContainer = getThanosEffectContainer;
//    }
//}
//
