package com.geekholt.butterknife;

import android.app.Activity;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 吴灏腾
 * @date 2019-11-07
 * @describe TODO
 */
public class ButterKnife {
    private static final String SUFFIX = "$BindAdapterImp";
    //做了一个缓存，只有第一次bind时才通过反射创建对象
    static Map<Class, BindAdapter> mBindCache = new HashMap();

    public static void bind(Activity target) {
        BindAdapter bindAdapter = null;
        if (mBindCache.get(target) != null) {
            //如果缓存中有activity，从缓存中取
            bindAdapter = mBindCache.get(target);
        } else {
            //缓存中没有，创建一个
            try {
                String adapterClassName = target.getClass().getName() + SUFFIX;
                Class<?> aClass = Class.forName(adapterClassName);
                bindAdapter = (BindAdapter) aClass.newInstance();
                mBindCache.put(aClass, bindAdapter);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        //调用bind
        if (bindAdapter != null) {
            bindAdapter.bind(target);
        }
    }
}
