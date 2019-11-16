package com.zj.action;



import com.zj.annotation.ZJController;
import com.zj.annotation.ZJRequestMapping;
import com.zj.annotation.ZJRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

@ZJController
public class DemoAction {

    @ZJRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @ZJRequestParam("name") String name){

        try {
            resp.setHeader("content-type", "text/html;charset=UTF-8");
            resp.setCharacterEncoding("utf-8");
            resp.getWriter().write("你输入的是:"+name);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @ZJRequestMapping("/zj")
    public void getZj(HttpServletRequest request,HttpServletResponse response){
        response.setHeader("content-type", "text/html;charset=UTF-8");
        response.setCharacterEncoding("utf-8");
        try {
            response.getWriter().write("对了对了，第一个spring框架完成了！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
