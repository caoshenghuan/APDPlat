package com.apdplat.module.index.service;

import com.apdplat.module.index.model.IndexScheduleConfig;
import com.apdplat.platform.log.APDPlatLogger;
import com.apdplat.platform.result.Page;
import com.apdplat.platform.service.ServiceFacade;
import java.text.ParseException;
import javax.annotation.Resource;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.quartz.CronTriggerBean;
import org.springframework.stereotype.Service;

@Service
public class IndexSchedulerService implements ApplicationListener {
    protected static final APDPlatLogger log = new APDPlatLogger(IndexSchedulerService.class);

    private static SchedulerFactory sf = new StdSchedulerFactory();
    @Resource(name = "serviceFacade")
    protected ServiceFacade serviceFacade;
    @Resource(name = "indexTask")
    private JobDetail indexTask;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            log.info("spring容器初始化完成, 开始检查是否需要启动定时索引调度器");
            IndexScheduleConfig config = getIndexScheduleConfig();
            if (config != null && config.isEnabled()) {
                schedule(config.getScheduleHour(),config.getScheduleMinute());
                log.info("启动定时重建索引调度器");
            }else{
                log.info("没有设置定时重建索引任务");
            }
        }
    }
    
    public IndexScheduleConfig getIndexScheduleConfig(){        
        Page<IndexScheduleConfig> page=serviceFacade.query(IndexScheduleConfig.class);
        if(page.getTotalRecords()==1){
            IndexScheduleConfig scheduleConfig=page.getModels().get(0);  
            return scheduleConfig;
        }
        return null;
    }

    public String unSchedule(){        
        try {
            IndexScheduleConfig config=getIndexScheduleConfig();
            if(config!=null){
                config.setEnabled(false);
                serviceFacade.update(config);
                log.info("禁用定时重建配置对象");
            }else{
                String tip="还没有设置定时重建索引任务";
                log.info(tip);
                return tip;
            }
            Scheduler sched = sf.getScheduler();
            sched.deleteJob(indexTask.getName(), "DEFAULT");
            String tip="删除定时重建索引任务，任务名为：" + indexTask.getName() + ",全名为: " + indexTask.getFullName();
            log.info(tip);
            return tip;
        } catch (SchedulerException ex) {
            String tip="删除定时重建索引任务失败，原因："+ex.getMessage();
            log.info(tip);
            return tip;
        }
    }

    public String schedule(int hour, int minute) {
        IndexScheduleConfig scheduleConfig = getIndexScheduleConfig();
        if (scheduleConfig == null) {
            //新建配置对象
            IndexScheduleConfig config = new IndexScheduleConfig();
            config.setScheduleHour(hour);
            config.setScheduleMinute(minute);
            config.setEnabled(true);
            serviceFacade.create(config);
        } else {
            //修改配置对象
            scheduleConfig.setScheduleHour(hour);
            scheduleConfig.setScheduleMinute(minute);
            scheduleConfig.setEnabled(true);
            serviceFacade.update(scheduleConfig);
        }

        String expression = "0 " + minute + " " + hour + " * * ?";
        try {
            CronExpression cronExpression = new CronExpression(expression);

            CronTrigger trigger = new CronTriggerBean();
            trigger.setCronExpression(cronExpression);
            trigger.setName("定时触发器,时间为：" + hour + ":" + minute);

            Scheduler sched = sf.getScheduler();
            sched.deleteJob(indexTask.getName(), "DEFAULT");
            sched.scheduleJob(indexTask, trigger);
            sched.start();
            String tip = "删除上一次的任务，任务名为：" + indexTask.getName() + ",全名为: " + indexTask.getFullName();
            log.info(tip);
            String taskState = "定时重建索引任务执行频率为每天，时间（24小时制）" + hour + ":" + minute;
            log.info(taskState);
            return taskState;
        } catch (ParseException | SchedulerException ex) {
            String tip = "定时重建索引设置失败，原因：" + ex.getMessage();
            log.info(tip,ex);
            return tip;
        }
    }
}
