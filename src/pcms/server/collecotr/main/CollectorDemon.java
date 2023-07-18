package pcms.server.collecotr.main;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import pcms.server.collector.dao.LogCollection;
import pcms.server.collector.dao.LogInsert;
import pcms.server.collector.db.ConnectionPLDM;
import pcms.server.collector.db.ConnectionPool;
import pcms.server.collector.util.CommonUtil;
import pcms.server.collector.util.Config;
import pcms.server.collector.util.DateUtil;
import pcms.server.collector.util.Log;
import pcms.server.collector.util.OSValidator;

public class CollectorDemon implements Runnable {
	private boolean isRun = true;
	private boolean isCollect = true;
	private String logFilePathFullName = Config.Path.LogFilePath;
	private static Connection con = null;

	public void run() {
		// TODO Auto-generated method stub
		try {
			Log.TraceLog("Collector Demon이 시작되었습니다...", "INFO");
			
			Config.Path.connectionPool = ConnectionPool.getInstance();
			Config.Path.connectionPLDM = ConnectionPLDM.getPLDMInstance();
			

			while (isRun) {
				isCollect = Boolean.parseBoolean(CommonUtil.getPropertiesInfo("isCollect"));
				if(isCollect){
					try {
						//테이블 조회 수
						int tableCnt = Integer.parseInt(CommonUtil.getPropertiesInfo("collect_count"));
						int logCount = 0;
						int limit_count = 0;
						int totCount = 0;
						int procCount = 0;
						Date sDate = new Date();
						Date eDate = new Date();
						boolean bInsert = false;
						String isTrafficLimit = "";

						limit_count = Integer.parseInt(CommonUtil.getPropertiesInfo("loglimit"));
						isTrafficLimit = CommonUtil.getPropertiesInfo("isTrafficLimit");

						con = Config.Path.connectionPLDM.getConnection();

						for(int i=1;i<=tableCnt;i++){
							
							/**
							 * 로그 조회 테이블 정보 읽음
							 */
							String collect_query = CommonUtil.getPropertiesInfo("collect_query" + i);
							String condition_column = CommonUtil.getPropertiesInfo("condition_column" + i);
							String tableName = CommonUtil.getPropertiesInfo("collect_tablename" + i);
							String condition_query = CommonUtil.getPropertiesInfo("condition_query" + i);
							//String condition_last_value = CommonUtil.getPropertiesInfo(tableName, Config.Path.ConditionValuePath);
							String copy_query = CommonUtil.getPropertiesInfo("copy_query" + i);
							String fwid_title = CommonUtil.getPropertiesInfo("fwid" + i);
							String if_index_title = CommonUtil.getPropertiesInfo("if_index" + i);
//							String emp_no_title = CommonUtil.getPropertiesInfo("emp_no" + i);
//							String emp_ip_title = CommonUtil.getPropertiesInfo("emp_ip" + i);
//							String emp_mac_title = CommonUtil.getPropertiesInfo("emp_mac" + i);
							String log_org_date_title = CommonUtil.getPropertiesInfo("log_org_date" + i);
							String proc_date = CommonUtil.getPropertiesInfo("proc_date");
							StringBuffer logBuffer = new StringBuffer();
							StringBuffer logInsert = new StringBuffer();
							
							int proc_cnt = 0;
							proc_cnt = Integer.parseInt(proc_date);
							String condition_last_value = DateUtil.getDateAdd(Calendar.DATE, -proc_cnt);
							
							System.out.println("[ condition query ] " + condition_query + String.format(" [ condition value ] '%s'",condition_last_value));
							Log.TraceLog("[ condition query ] " + condition_query + String.format(" [ condition value ] '%s'",condition_last_value), "DEBUG");
							condition_query = condition_query.replace("$$condition_value$$", String.format("'%s'",condition_last_value));
							if(!condition_query.equals("")){
								collect_query = collect_query + " WHERE " + condition_query + " GROUP BY fwid, if_index, collect_date ";
							}
						
							System.out.println("[ collect_query ] " + collect_query);
							Log.TraceLog("[ collect_query ] " + collect_query, "DEBUG") ;
							/**
							 * 로그 저장 정보 및 파일명 
							 */
							
							SimpleDateFormat formatter1 = new SimpleDateFormat ("yyyyMMddHHmmss");
							String logDate = formatter1.format(new Date());
							logFilePathFullName = logFilePathFullName + String.format("%s_%s.log", logDate, tableName);
							 
							String condition_value = "";
							
					      	StringBuffer query = new StringBuffer();
					      	
							/**
							 * NMS 장비별 인터페이스별 최번시 in_bps, out_bps 조회
							 */
							List<LinkedHashMap<String, String>> logList = LogCollection.getLogCollect(collect_query);


							if(logList.size() > 0){
								Log.TraceLog("Data Parsing Start", "DEBUG");
								sDate = new Date();
								for(LinkedHashMap<String, String> row:logList){
									logCount++;
									totCount++;
									logBuffer = new StringBuffer();
									
									String fwID = "";
									String if_index = "";
									String fwname = "";
									String ifname = "";
									String collect_date = "";
									String max_in_bps = "";
									String max_out_bps = "";
									
									fwID = row.get(fwid_title);
									if_index = row.get(if_index_title);
									fwname = row.get("fwname");
									ifname = row.get("fwname");
									collect_date = row.get("collect_date");
									max_in_bps = row.get("max_in_bps");
									max_out_bps = row.get("max_out_bps");
									
									if (fwID == null)
									{
										continue;
									}
							      	query = new StringBuffer();

						      		query.append("select cir_num from kds_circuit_if_info where fwid = '" + fwID + "' and if_index = '" + if_index + "';");

						      		PreparedStatement pstmt = null;


									pstmt = con.prepareStatement(query.toString());
										
									ResultSet rs = pstmt.executeQuery();
									
									String cir_num = "";
									while(rs.next()){
										cir_num = rs.getString(1);
										logBuffer.append(String.format("%s%s%s", "\"", cir_num, "\"|"));
										break;
									}
									
									if(cir_num.equals(""))
									{
										continue;
									}

									query = new StringBuffer();
									
						      		query.append("select count(*) from kds_circuit_traffic_info where cir_num = '" + cir_num + "' and collect_date = '" + collect_date + "';");

									pstmt = con.prepareStatement(query.toString());
										
									rs = pstmt.executeQuery();

									int cir_cnt = 0;
									while(rs.next()){
										cir_cnt = Integer.parseInt(rs.getString(1));
										break;
									}
									
									if (cir_cnt <= 0) {
	
										String InsertSql = "INSERT INTO kds_circuit_traffic_info(cir_num, fwid, fwname, if_index, collect_date, max_in_bps, max_out_bps, rgdt_date)VALUES(?,?,?,?,?,?,?,now());";
	
										pstmt = (PreparedStatement)con.prepareStatement(InsertSql);
										
					        			pstmt.setString(1, cir_num);
					                    pstmt.setString(2, fwID);
					        			pstmt.setString(3, fwname);
					                    pstmt.setString(4, if_index);
					        			pstmt.setString(5, collect_date);
					        			pstmt.setString(6, max_in_bps);
					        			pstmt.setString(7, max_out_bps);
	
					        			pstmt.execute();
					        			pstmt.clearParameters();
					        			procCount++;
				        			
									}

//									for(String key:row.keySet()){
//										logBuffer.append(String.format("%s%s%s", "\"", row.get(key), "\"|"));
//									}
//									// 마지막 문자 '|' 삭제
//									logBuffer.deleteCharAt(logBuffer.length() - 1);
//									// 마지막 조회 일자 저장
//									condition_value = row.get(condition_column);
//
//									logInsert.append(logBuffer.toString() + "\r\n");
//									
//									if(logCount%limit_count == 0){
//										bInsert = LogInsert.setLogInsert(copy_query, logInsert);
//										logInsert = new StringBuffer();
//										logCount = 0;
//									}
								}

//								if(logCount > 0){
//									bInsert = LogInsert.setLogInsert(copy_query, logInsert);
//								}
								eDate = new Date();
								Log.TraceLog("Data Insert End - Proc Count : " + procCount, "DEBUG");
								System.out.println("Data Insert End - Proc Count : " + procCount);
								totCount = 0;

								/**
								 * 검색조건 (마지막 날짜 정보) 저장
								 */
								if (bInsert) {
									System.out.println(condition_value);
									CommonUtil.setPropertiesInfo(tableName, condition_value, Config.Path.ConditionValuePath);
								}
								

								
							}else{
								condition_value = condition_last_value;
								CommonUtil.setPropertiesInfo(tableName, condition_value, Config.Path.ConditionValuePath);
								Log.TraceLog("[ " + tableName + " ] Log 수집 데이터 없음..... 파일생성 및 전송 미처리...", "INFO");
								System.out.println("[ " + tableName + " ] Log 수집 데이터 없음..... 파일생성 및 전송 미처리...");
							}
							
					      	query = new StringBuffer();

				      		query.append("select cir_num from kds_circuit_if_info;");

				      		PreparedStatement pstmt = null;

							con = Config.Path.connectionPLDM.getConnection();

							pstmt = con.prepareStatement(query.toString());
								
							ResultSet rs = pstmt.executeQuery();
							
							
							
							String cir_num = "";
							while(rs.next()){
								cir_num = rs.getString(1);

						      	query = new StringBuffer();
						      	
						      	query.append("select cir_speed from rra_scp_info where lower_cir_num  = '" + cir_num + "' or upper_cir_num = '" + cir_num + "'; ");
					      		
								pstmt = con.prepareStatement(query.toString());
									
								ResultSet rs_cir_speed = pstmt.executeQuery();
								
								double cir_speed_num = 1;
								
								while(rs_cir_speed.next()) {
									String cir_speed = "";
									cir_speed = rs_cir_speed.getString(1);
									if (cir_speed == null) 
									{
										cir_speed_num = 1;
										break;
									}
									else
									{
										if (cir_speed.equals("2") || cir_speed.equals("2.048"))
										{
											cir_speed_num = 0.2;
											break;
										}
										if (cir_speed.equals("4"))
										{
											cir_speed_num = 0.4;
											break;
										}
										if (cir_speed.equals("10"))
										{
											cir_speed_num = 1;
											break;
										}
										else if (cir_speed.equals("30"))
										{
											cir_speed_num = 3;
											break;
										}
										else if (cir_speed.equals("50"))
										{
											cir_speed_num = 5;
											break;
										}
										else if (cir_speed.equals("100"))
										{
											cir_speed_num = 10;
											break;
										}
										else if (cir_speed.equals("300"))
										{
											cir_speed_num = 30;
											break;
										}
										else if (cir_speed.equals("1G"))
										{
											cir_speed_num = 100;
											break;
										}
										else if (cir_speed.equals("2G"))
										{
											cir_speed_num = 200;
											break;
										}
										else
										{
											cir_speed_num = 1;
											break;
										}
									}
								}
								
						      	query = new StringBuffer();
						      	
						      	query.append("select cir_num, date_format(now(), '%Y%m%d') as collect_dt, sum(max_in_bps)/count(cir_num), sum(max_out_bps)/count(cir_num) from kds_circuit_traffic_info where cir_num = '" + cir_num + "' and collect_date > date_add(now(), interval -90 Day) and (max_in_bps > " + isTrafficLimit + " * " + cir_speed_num + " or max_out_bps > " + isTrafficLimit  + " * " + cir_speed_num + "); ");
					      		
//								System.out.println("[ Summary_query ] " + query);
//								Log.TraceLog("[ Summary_query ] " + query, "DEBUG") ;
								
								pstmt = con.prepareStatement(query.toString());
									
								ResultSet rs1 = pstmt.executeQuery();
								
								while(rs1.next()){
									String cir_num_tmp = rs1.getString(1);
									String collect_dt = rs1.getString(2);

									if (cir_num_tmp == null)
									{
										continue;
									}
							      	query = new StringBuffer();
							      	
						      		query.append("select count(*) from kds_circuit_90Day_Avg_info where cir_num = '" + cir_num_tmp + "'; ");
						      		
									pstmt = con.prepareStatement(query.toString());
										
									ResultSet rs2 = pstmt.executeQuery();
									while (rs2.next()) {
										if(rs2.getInt(1) > 0)
										{
											long avg_in_bps = rs1.getLong(3);
											long avg_out_bps = rs1.getLong(4);
											
											String UpdateSql = "Update kds_circuit_90Day_Avg_info set avg_in_bps = " + avg_in_bps + ", avg_out_bps = " + avg_out_bps + ", rgdt_date = now() where cir_num = '" + cir_num + "';";
											
											pstmt = (PreparedStatement)con.prepareStatement(UpdateSql);
										}
										else
										{
											String InsertSql = "INSERT INTO kds_circuit_90Day_Avg_info(cir_num, avg_in_bps, avg_out_bps, rgdt_date)VALUES(?,?,?,now());";
											
											pstmt = (PreparedStatement)con.prepareStatement(InsertSql);
											
						        			pstmt.setString(1, rs1.getString(1));
						            		pstmt.setLong(2, rs1.getLong(3));;
						                    pstmt.setLong(3, rs1.getLong(4));
										}

					        			pstmt.execute();
					        			pstmt.clearParameters();
					        			
					        			break;
									}
									
								}
								
							}

						}
						
						Log.TraceLog("Data 90Day Avg proc End", "DEBUG");
						System.out.println("Data 90Day Avg proc End");							
						
					} catch (Exception e) {
						// TODO Auto-generated catch block
						Log.TraceLog(e.toString(), "DEBUG");
						e.printStackTrace();
						
					}finally{
						
						//sftpTransfer.close();
					}

				}
				
				int collect_interval = 1000 * Integer.parseInt(CommonUtil.getPropertiesInfo("collect_interval"));
				if(collect_interval == 0){
					break;
				}
				Thread.sleep(collect_interval);//쓰레드를 잠시 멈춤
				isRun = Boolean.parseBoolean(CommonUtil.getPropertiesInfo("isRun"));
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.TraceLog(e.getStackTrace());
			e.printStackTrace();

		}
	}
}