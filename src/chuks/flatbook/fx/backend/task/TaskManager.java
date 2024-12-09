/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task;

import chuks.flatbook.fx.backend.task.Task;
import util.TaskResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class TaskManager {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    List<Task> tasks = Collections.synchronizedList(new ArrayList());
    private boolean stop = false;
    private Task currentTask;

    public TaskManager() {
        executor.submit(new TaskHandler()::run);
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    public Task getCurrennTask() {
        return currentTask;
    }

    public void stop() {
        this.stop = true;
        executor.shutdown();
    }

    class TaskHandler implements Runnable {

        private static final org.slf4j.Logger logger = LoggerFactory.getLogger(TaskHandler.class.getName());

        @Override
        public void run() {
            CompletableFuture<TaskResult> future = null;
            TaskResult result;
            while (!stop) {
                for (int i = 0; i < tasks.size(); i++) {
                    currentTask = tasks.remove(i);

                    if (currentTask != null) {//checking for null - just in case, since we are dealing with thread
                        try {

                            result = currentTask.run().get();

                            handleTaskResult(currentTask, result);
                            
                        } catch (InterruptedException | ExecutionException ex) {
                            logger.error("Task run failed - " + ex.getMessage(), ex);
                        }
                    }
                }
            }
        }

        private void handleTaskResult(Task task, TaskResult result) {
            
            
        }
    }
}
