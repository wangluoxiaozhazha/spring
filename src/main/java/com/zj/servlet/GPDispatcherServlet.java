package com.zj.servlet;


import com.zj.annotation.ZJAutowired;
import com.zj.annotation.ZJController;
import com.zj.annotation.ZJRequestMapping;
import com.zj.annotation.ZJService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class GPDispatcherServlet extends HttpServlet {

    private static final long serialVersionUID=1L;
    //跟web.xml中的param-name一致
    private static final String LOCATION="contextConfigLocation";
    //配置信息
    private Properties p=new Properties();
    //保存被扫描的相关类
    private List<String> classNames=new ArrayList<String>();
    //保存初始化Bean
    private Map<String,Object> ioc=new HashMap<String, Object>();
    //url与方法映射关系
    private Map<String, Method> handlerMapping=new HashMap<String, Method>();

    public GPDispatcherServlet(){super();}

    /**
     * 初始化，加载配置文件
     * @param config
     * @throws ServletException
     */
    public void init(ServletConfig config) throws ServletException{

        //加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //扫描所有相关类
        doScanner(p.getProperty("scanPackage"));

        //初始化所有类，并保存在容器中
        doInstance();

        //依赖注入
        doAutowired();

        //构造handlermapping
        initHandlerMapping();

    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        this.doPost(req,resp);
    }

    /**
     * 业务逻辑处理
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            doDispatch(req,resp);
        }catch (Exception e){
            resp.getWriter().write("500 Exception,Details:\r\n"+Arrays.toString(e.getStackTrace())
            .replaceAll("\\[|\\]","").replaceAll(",\\s","\r\n"));
        }
    }

    /**
     * 加载配置文件
     * @param location
     */
    private void doLoadConfig(String location){

        InputStream fis=null;
        try {
            fis=this.getClass().getClassLoader().getResourceAsStream(location);
            p.load(fis);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if (fis!=null)
                    fis.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 扫描每个类
     * @param packageName
     */
    private void doScanner(String packageName){
        //所有包路径转文件路径
        URL url=this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.","/"));
        File dir=new File(url.getFile());
        for (File file:dir.listFiles()){
            //如果是文件夹，继续递归
            if (file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            }else {
                classNames.add(packageName+"."+file.getName().replace(".class","").trim());
            }
        }
    }

    /**
     * 首字母处理
     * @param str
     * @return
     */
    private String lowerFirstCase(String str){
        char[] chars=str.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    /**
     * 初始化所有相关类并放入ioc
     */
    private void doInstance(){

        if (classNames.size()==0){return;}
        try {
            for (String className:classNames){
                Class<?> clazz=Class.forName(className);
                if (clazz.isAnnotationPresent(ZJController.class)){
                    //默认将首字母小写做beanName
                    String beanName=lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }
                else if (clazz.isAnnotationPresent(ZJService.class)){
                    ZJService service=clazz.getAnnotation(ZJService.class);
                    String beanName=service.value();
                    //如果用户有自己的名字就用自己的
                    if (!"".equals(beanName.trim())){
                        ioc.put(beanName,clazz.newInstance());
                        continue;
                    }

                    Class<?>[] interfaces=clazz.getInterfaces();
                    for (Class<?> i:interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }
                else {
                    continue;
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 将初始化到IOC容器中的类，需要赋值的字段进行赋值。
     */
    private void doAutowired(){
        if (ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry:ioc.entrySet()){
            //拿到所有属性
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for (Field field:fields){

                if (!field.isAnnotationPresent(ZJAutowired.class)){continue;}

                ZJAutowired autowired=field.getAnnotation(ZJAutowired.class);
                String beanName=autowired.value().trim();
                if ("".equals(beanName)){
                    beanName=field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 将GPRequestMapping中配置的信息和Method进行关联，并保存这些关系
     */
    private void initHandlerMapping(){

        if (ioc.isEmpty()){return;}
        for (Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz=entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(ZJController.class)){continue;}

            String beanUrl="";
            //获取controller的url配置
            if (clazz.isAnnotationPresent(ZJRequestMapping.class)){
                ZJRequestMapping requestMapping=clazz.getAnnotation(ZJRequestMapping.class);
                beanUrl=requestMapping.value();
            }

            //获取方法的URL配置
            Method[] methods=clazz.getMethods();
            for (Method method:methods){
                //没有加注解的直接忽略
                if (!method.isAnnotationPresent(ZJRequestMapping.class)){return;}

                //映射url
                ZJRequestMapping requestMapping=method.getAnnotation(ZJRequestMapping.class);
                String url=("/"+beanUrl+"/"+requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);
            }
        }
    }

    private void doDispatch(HttpServletRequest reg,HttpServletResponse resp) throws Exception{

        if (this.handlerMapping.isEmpty()){return;}

        String url=reg.getRequestURI();
        String contextPath=reg.getContextPath();
        url=url.replace(contextPath,"").replaceAll("/+","/");

        if (!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!");
            return;
        }

        Map<String,String[]> params=reg.getParameterMap();
        Method method=this.handlerMapping.get(url);
        String beanName=lowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(this.ioc.get(beanName),reg,resp);
    }
}
