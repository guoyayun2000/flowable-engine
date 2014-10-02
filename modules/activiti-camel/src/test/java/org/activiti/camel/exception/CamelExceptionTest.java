/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.camel.exception;

import java.util.List;

import org.activiti.camel.exception.tools.ExceptionServiceMock;
import org.activiti.camel.exception.tools.NoExceptionServiceMock;
import org.activiti.camel.exception.tools.ThrowBpmnExceptionBean;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.Job;
import org.activiti.engine.test.Deployment;
import org.activiti.spring.impl.test.SpringActivitiTestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;


/**
 * @author Saeid Mirzaei  
 */
@ContextConfiguration("classpath:generic-camel-activiti-context.xml")
public class CamelExceptionTest extends SpringActivitiTestCase{
  
  @Autowired
  CamelContext camelContext;
  
  @Autowired
  RuntimeService runtimeService;
  
  @Autowired
  ManagementService managementService;


  
  public void  setUp() throws Exception {
    ExceptionServiceMock.reset();
    NoExceptionServiceMock.reset();
    
    camelContext.addRoutes(new RouteBuilder() {    
    @Override
    public void configure() throws Exception {
        from("activiti:exceptionInRouteSynchron:errorCamelTask").to("log:helloWorld").bean(ThrowBpmnExceptionBean.class);
        }
     });
  }
  
  public void tearDown() throws Exception {
    List<Route> routes = camelContext.getRoutes();
    for (Route r: routes) {
      camelContext.stopRoute(r.getId());
      camelContext.removeRoute(r.getId());
    }
    
  }
  
  
  
  // If no exception is thrown, a normal happy path is expected
  @Deployment(resources={"org/activiti/camel/exception/bpmnExceptionInRouteSynchron.bpmn20.xml"})
  public void testHappyPathSynchron() {
    
    // Signal ThrowBpmnExceptionBean to throw no exception
    ThrowBpmnExceptionBean.setExceptionType(ThrowBpmnExceptionBean.ExceptionType.NO_EXCEPTION);    
    runtimeService.startProcessInstanceByKey("exceptionInRouteSynchron");
    
    assertFalse(ExceptionServiceMock.isCalled());
    assertTrue(NoExceptionServiceMock.isCalled());

  }
  
  @Deployment(resources={"org/activiti/camel/exception/bpmnExceptionInRouteSynchron.bpmn20.xml"})
  public void testNonBpmnExceptionInCamel() {
    // Signal ThrowBpmnExceptionBean to throw a non BPMN Exception
    ThrowBpmnExceptionBean.setExceptionType(ThrowBpmnExceptionBean.ExceptionType.NON_BPMN_EXCEPTION);    

    try {
      runtimeService.startProcessInstanceByKey("exceptionInRouteSynchron");
    } catch (ActivitiException e) {
      assertEquals(Exception.class, e.getCause().getClass());
      assertEquals("arbitary non bpmn exception", e.getCause().getMessage());
      
      assertFalse(ExceptionServiceMock.isCalled());
      assertFalse(NoExceptionServiceMock.isCalled());
      
      return;
   }
   fail("Activiti exception expected");
  }
  
  @Deployment(resources={"org/activiti/camel/exception/bpmnExceptionInRouteSynchron.bpmn20.xml"})
  public void testBpmnExceptionInCamel() {
    // Signal ThrowBpmnExceptionBean to throw a non BPMN Exception
    ThrowBpmnExceptionBean.setExceptionType(ThrowBpmnExceptionBean.ExceptionType.BPMN_EXCEPTION);    

    try {
      runtimeService.startProcessInstanceByKey("exceptionInRouteSynchron");
    } catch (ActivitiException e) {
      fail("The exception should be handled by camel. No exception expected.");
   }
   assertTrue(ExceptionServiceMock.isCalled());
   assertFalse(NoExceptionServiceMock.isCalled());    
    
  }


  // If no exception is thrown, a normal happy path is expected
  @Deployment(resources={"org/activiti/camel/exception/bpmnExceptionInRouteAsynchron.bpmn20.xml"})
  public void testHappyPathAsynchron() {
    
    // Signal ThrowBpmnExceptionBean to throw no exception
    ThrowBpmnExceptionBean.setExceptionType(ThrowBpmnExceptionBean.ExceptionType.NO_EXCEPTION);    
    runtimeService.startProcessInstanceByKey("exceptionInRouteSynchron");
    assertTrue(areJobsAvailable());
    waitForJobExecutorToProcessAllJobs(1000, 100);
    assertFalse(areJobsAvailable());
    assertFalse(ExceptionServiceMock.isCalled());
    assertTrue(NoExceptionServiceMock.isCalled());

  }
  
  // If no exception is thrown, a normal happy path is expected
  @Deployment(resources={"org/activiti/camel/exception/bpmnExceptionInRouteAsynchron.bpmn20.xml"})
  public void testNonBpmnPathAsynchron() {
    
    // Signal ThrowBpmnExceptionBean to throw no exception
    ThrowBpmnExceptionBean.setExceptionType(ThrowBpmnExceptionBean.ExceptionType.NON_BPMN_EXCEPTION);    
    runtimeService.startProcessInstanceByKey("exceptionInRouteSynchron");
    assertTrue(areJobsAvailable());
    int nErrorJobs =  (int) managementService.createJobQuery().count();
    
    assertEquals(1, nErrorJobs);
    Job camelJob = managementService.createJobQuery().singleResult();
    System.out.println(camelJob.getExceptionMessage());
    waitForJobExecutorToProcessAllJobs(10000000, 100);
    System.out.println(camelJob.getExceptionMessage());
        
    nErrorJobs =  (int) managementService.createJobQuery().exceptionMessage("arbitary non bpmn exception").count();
    assertEquals(1, nErrorJobs);
    
    assertFalse(ExceptionServiceMock.isCalled());
    assertFalse(NoExceptionServiceMock.isCalled());
    assertFalse(areJobsAvailable());

  }
  
}
