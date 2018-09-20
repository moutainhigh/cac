package net.engining.pcx.cc.process.service.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.engining.gm.infrastructure.enums.Interval;
import net.engining.pcx.cc.param.model.Account;
import net.engining.pcx.cc.param.model.InterestTable;
import net.engining.pcx.cc.param.model.RateCalcMethod;
import net.engining.pcx.cc.param.model.enums.PaymentMethod;
import net.engining.pcx.cc.process.model.PaymentPlanDetail;
import net.engining.pcx.cc.process.service.support.Provider7x24;
import net.engining.pg.support.utils.DateUtilsExt;
import net.engining.pg.support.utils.ValidateUtilExt;

/**
 * 还款计划相关计算辅助服务
 * 
 * @author luxue
 *
 */
@Service
public class NewPaymentPlanCalcService {

	@Autowired
	private NewComputeService newComputeService;
	
	@Autowired
	Provider7x24 provider7x24;
	
	/**
	 * 根据系统业务日期计算与当前自然日的偏移量
	 * @param bizDate
	 * @param interval
	 * @return n days or n week or n month or n year
	 */
	public int getOffset4BizDate2NatureDate(LocalDate bizDate, Interval interval) {
		int offset = 0;
		Period period = null;
		LocalDate natureDate = new LocalDate(DateUtilsExt.truncate(new Date(), Calendar.DATE));
		if(ValidateUtilExt.isNullOrEmpty(bizDate)){
			bizDate = provider7x24.getCurrentDate();
		}
		switch (interval) {
			case D:
				//bizDate大于natureDate为负数，反之正数
				period = new Period(bizDate, natureDate, PeriodType.days());
				offset = period.getDays();
				break;
			case W:
				//bizDate大于natureDate为负数，反之正数
				period = new Period(bizDate, natureDate, PeriodType.weeks());
				offset = period.getWeeks();
				break;
			case M:
				//bizDate大于natureDate为负数，反之正数
				period = new Period(bizDate, natureDate, PeriodType.months());
				offset = period.getMonths();
				break;
			case Y:
				//bizDate大于natureDate为负数，反之正数
				period = new Period(bizDate, natureDate, PeriodType.years());
				offset = period.getYears();
				break;	
		}
		
		return offset;
	}
	
	public static void main(String[] args) {
		LocalDate bizDate = new LocalDate(2018, 9, 22);
		LocalDate natureDate = new LocalDate(2018, 9, 20);
		Period period = new Period(bizDate, natureDate, PeriodType.days());
		int offset = period.getDays();
		System.out.println(offset);
	}
	
	private Date caculatePaymentDate(Interval interval, Boolean intFirstPeriodAdj, PaymentMethod paymentMethod,
			Date startDate, int fixedDay, Integer mult, int pmtDueDays, int i){
		Date date = null;
		switch (interval) {
			case D:
				if (intFirstPeriodAdj != null && intFirstPeriodAdj) {
					date = DateUtils.addDays(DateUtils.addDays(startDate, mult * (i + 1)), pmtDueDays);
				} else {
					date = DateUtils.addDays(DateUtils.addDays(DateUtils.addDays(startDate, 0), mult * (i + 1)), pmtDueDays);
				}
				break;
			case W:
				date = DateUtils.addDays(DateUtils.addDays(startDate, mult * (i + 1) * 7), pmtDueDays);
				break;
			case M:
				switch (paymentMethod) {
				// TODO 待重构，增加固定日还款独立参数，不要通过还款方式区分，理论上，目前大部分支持分期的还款方式都可以支持固定日还款
				case MRG:
				case MSF:
					if(fixedDay==0){
						throw new IllegalArgumentException("固定还款日参数不可为0");
					}
					// 根据固定还款日计算方式，以28日作为分界，因为大小月的原因，业务上规定28日之后都以28日作为还款日
					// 先计算固定还款日对应的日历
					Calendar fixedCalendar = Calendar.getInstance();
					fixedCalendar.setTime(startDate);
					fixedDay = fixedDay >= 28 ? 28 : fixedDay;
					fixedCalendar.set(fixedCalendar.get(Calendar.YEAR), fixedCalendar.get(Calendar.MONTH), fixedDay);
					Date fixedDate = fixedCalendar.getTime();
					//计算入账日对应的日历
					Calendar postCalendar = Calendar.getInstance();
					postCalendar.setTime(startDate);
					
					//假设每月固定5日还款;
					//如果postDate=1月1日，那么第一次还款应该为1月5日；
					//如果postDate=1月10日，那么第一次还款应该为2月5日；
					if(i==0){
						if( postCalendar.get(Calendar.DAY_OF_MONTH) <= fixedDay){
							date = DateUtils.addDays(DateUtils.addMonths(fixedDate, mult * (i + 1)), pmtDueDays);
						}
						else{
							date = DateUtils.addDays(DateUtils.addMonths(DateUtils.addMonths(fixedDate, 1), mult * (i + 1)), pmtDueDays);
						}
					}
					else{
						date = DateUtils.addDays(DateUtils.addMonths(fixedDate, mult * (i + 1)), pmtDueDays);
					}
					break;
				default:
					date = DateUtils.addDays(DateUtils.addMonths(startDate, mult * (i + 1)), pmtDueDays);
					break;
				}
				break;
			case Y:
				date = DateUtils.addDays(DateUtils.addYears(startDate, mult * (i + 1)), pmtDueDays);
				break;
			}
		return date;
	}
	
	public PaymentPlanDetail setupPaymentNatureDate(Interval interval, Boolean intFirstPeriodAdj, PaymentMethod paymentMethod,
			Date startDate, Integer mult, int pmtDueDays, int i, PaymentPlanDetail detail) {
		return setupPaymentNatureDate(interval, intFirstPeriodAdj, paymentMethod, startDate, 0, mult, pmtDueDays, i, detail);
	}
	
	public PaymentPlanDetail setupPaymentNatureDate(Interval interval, Boolean intFirstPeriodAdj, PaymentMethod paymentMethod,
			Date startDate, int fixedDay, Integer mult, int pmtDueDays, int i, PaymentPlanDetail detail) {
		Date date = caculatePaymentDate(interval, intFirstPeriodAdj, paymentMethod, startDate, fixedDay, mult, pmtDueDays, i);
		detail.setPaymentNatureDate(date);
		return detail;
	}
	
	/**
	 * 计算并设置还款计划明细相应期数的到期还款日期
	 * 
	 * @param interval
	 *            还款周期间隔单位
	 * @param intFirstPeriodAdj
	 *            首期天数是否调整
	 * @param paymentMethod
	 *            还款方式
	 * @param postDate
	 *            入账交易日期
	 * @param mult
	 *            周期乘数
	 * @param pmtDueDays
	 *            到期还款延后天数
	 * @param i
	 *            要计算的期数：从0开始，0代表第一期
	 * @param detail
	 *            还款计划明细对象
	 * @return
	 */
	public PaymentPlanDetail setupPaymentDate(Interval interval, Boolean intFirstPeriodAdj, PaymentMethod paymentMethod,
			Date postDate, Integer mult, int pmtDueDays, int i, PaymentPlanDetail detail) {
		return setupPaymentDate(interval, intFirstPeriodAdj, paymentMethod, postDate, 0, mult, pmtDueDays, i, detail);
	}

	/**
	 * 计算并设置还款计划明细相应期数的到期还款日期
	 * 
	 * @param interval
	 *            还款周期间隔单位
	 * @param intFirstPeriodAdj
	 *            首期天数是否调整
	 * @param paymentMethod
	 *            还款方式
	 * @param postDate
	 *            入账交易日期
	 * @param fixedDay
	 *            固定还款日
	 * @param mult
	 *            周期乘数
	 * @param pmtDueDays
	 *            到期还款延后天数
	 * @param i
	 *            要计算的期数：从0开始，0代表第一期
	 * @param detail
	 *            还款计划明细对象
	 * @return
	 */
	public PaymentPlanDetail setupPaymentDate(Interval interval, Boolean intFirstPeriodAdj, PaymentMethod paymentMethod,
			Date postDate, int fixedDay, Integer mult, int pmtDueDays, int i, PaymentPlanDetail detail) {
		
		Date date = caculatePaymentDate(interval, intFirstPeriodAdj, paymentMethod, postDate, fixedDay, mult, pmtDueDays, i);
		detail.setPaymentDate(date);
		return detail;
	}

	/**
	 * 计算并设置还款计划明细相应期数的应收利息
	 * 
	 * @param interval
	 *            还款周期间隔单位
	 * @param totalPeriod
	 *            总期数
	 * @param paymentMethod
	 *            还款方式
	 * @param interestParam
	 *            利率表参数
	 * @param i
	 *            要计算的期数：从0开始，0代表第一期
	 * @param postDate
	 *            入账交易日期
	 * @param mult
	 *            周期乘数
	 * @param calcRates
	 *            当期对应的利息计算方式
	 * @param leftBal
	 *            当期剩余本金
	 * @param loanAmount
	 *            贷款本金
	 * @param detail
	 *            还款计划明细对象
	 * @param lastPaymentDate
	 *            上期到期还款日期
	 * @return
	 */
	public PaymentPlanDetail setupInterestAmt(Interval interval, Integer totalPeriod, PaymentMethod paymentMethod,
			InterestTable interestParam, int i, Date postDate, Integer mult, List<RateCalcMethod> calcRates,
			BigDecimal leftBal, BigDecimal loanAmount, PaymentPlanDetail detail, Date lastPaymentDate) {
		// 如果是按每日计息的情况，用以下逻辑
		// 如果当前期数在最后一期之前，还款日当天的利息在还款日当天收掉(因为到期日当天还款，日终批量还是会在当期计一天利息)，最后一期的最后一天按时还款的话就不收利息。
		// 总体原则是当天放款当天收息，当天还款就不收息。
		// 例如:5月31日建账按月结息、按日计息、3期。
		// 第一期到期日是6月30日，计息时间5月31日-6月30日。
		// 第二期到期日是7月31日，第二期计息时间7月1日-7月31日。
		// 第三期到期日是8月31日，第三期计息时间8月1日-8月30日。
		// 另外需要注意，所有等息的情况不适合以下逻辑；即等息的还款方式要在业务上控制不支持每日计息；
		if (interestParam.cycleBase == Interval.D && interestParam.cycleBaseMult == 1) {
			// 开始计息日
			LocalDate startDate = new LocalDate(i == 0 ? postDate : lastPaymentDate);
			// 截止计息日
			LocalDate endDate = new LocalDate(detail.getPaymentDate());

			// 总期数大于1 且 当前期数小于总期数 且 按日每日计息；同时基于闭开区间原则此时截止计息日为本期还款日+1
			if (totalPeriod > 1 && i + 1 < totalPeriod && interestParam.cycleBase == Interval.D
					&& interestParam.cycleBaseMult == 1) {

				endDate = endDate.plusDays(1);
			}
			// 总期数大于1 且 当前期数大于0 且 按日每日计息；同时基于闭开区间原则此时开始计息日为上期还款日+1
			if (totalPeriod > 1 && i > 0 && interestParam.cycleBase == Interval.D && interestParam.cycleBaseMult == 1) {

				startDate = startDate.plusDays(1);
			}

			// 非等息的情况下，其他还款方式都是按剩余本金计算利息:按日按剩余本金计息(计息周期为日)
			if (paymentMethod != PaymentMethod.PSV && paymentMethod != PaymentMethod.PSZ && paymentMethod != PaymentMethod.MSF
					&& paymentMethod != PaymentMethod.MSV && paymentMethod != PaymentMethod.MSB) {
				detail.setInterestAmt(
						newComputeService.calcTieredAmount(interestParam.tierInd, calcRates, leftBal, leftBal)
								.multiply(BigDecimal.valueOf(Days.daysBetween(startDate, endDate).getDays()))
								.setScale(newComputeService.getReceivableScale(), RoundingMode.HALF_UP));
			} else {
				throw new IllegalArgumentException(paymentMethod + "等息类型的还款方式不可支持每日计息");
			}

		} else {// 按周期计息的情况
			// 等本等息的利息全部是按贷款总金额计算 (TODO 这里可能是业务特殊性造成的，到底是否按贷款总额计算，网上没有找到标准)
			if (paymentMethod == PaymentMethod.PSV || paymentMethod == PaymentMethod.PSZ) {
				detail.setInterestAmt(
						newComputeService.calcTieredAmount(interestParam.tierInd, calcRates, loanAmount, loanAmount)
								.multiply(BigDecimal.valueOf(mult))
								.setScale(newComputeService.getReceivableScale(), RoundingMode.HALF_UP));
			} else {
				detail.setInterestAmt((leftBal.multiply(calcRates.get(0).rate).multiply(BigDecimal.valueOf(mult)))
						.setScale(2, RoundingMode.HALF_UP));
			}

			// 以上逻辑对于利息分期的方式均会造成在不能整除时总利息差1分钱的情况；这种情况在业务上可以控制，不需要在意这1分钱；
			// TODO 待重构，中民项目一次还本按月付息，末期利息特殊处理
			if (i + 1 == totalPeriod && paymentMethod.equals(PaymentMethod.IFP)
					&& interestParam.rateBaseInterval.equals(Interval.Y) && interval.equals(Interval.M)) {

				BigDecimal lastInterestAmt = (newComputeService
						.calcTieredAmount(interestParam.tierInd, calcRates, leftBal, leftBal)
						.multiply(new BigDecimal(totalPeriod)))
								.subtract(detail.getInterestAmt().multiply(new BigDecimal(totalPeriod - 1)));
				detail.setInterestAmt(lastInterestAmt.setScale(2, RoundingMode.HALF_UP));
			}

		}

		// 利息前置特殊处理：分期先息后本(利息前置)类型，在最后一期由于利息前置已经在上期收取，所以最后一期利息为0；
		// TODO 待重构，增加利息前置独立参数，大部分支持分期的还款方式都可支持利息前置；
		if (paymentMethod == PaymentMethod.IIF && i == totalPeriod - 1) {

			detail.setInterestAmt(BigDecimal.ZERO);
		}

		return detail;
	}

	/**
	 * 计算并设置还款计划明细相应期数的应还本金
	 * @param paymentMethod
	 * @param totalPeriod
	 * @param i
	 * @param leftBal
	 * @param totalBal
	 * @param mult
	 * @param calcRates
	 * @param detail
	 * @return
	 */
	public TempPaymentPlanDetailExt setupPrincipalBal(PaymentMethod paymentMethod, Integer totalPeriod, int i,
			BigDecimal leftBal, BigDecimal totalBal, Integer mult, List<RateCalcMethod> calcRates,
			PaymentPlanDetail detail) {
		TempPaymentPlanDetailExt tempPaymentPlanDetailExt = new TempPaymentPlanDetailExt();
		switch (paymentMethod) {
		case MRT: //等本金且剩余靠后类型
		case PSV:
		case PSZ: {

			if (i == totalPeriod - 1) {
				detail.setPrincipalBal(leftBal);
				leftBal = BigDecimal.ZERO;
			} else {
				detail.setPrincipalBal(totalBal.divide(BigDecimal.valueOf(totalPeriod), 2, RoundingMode.HALF_UP));
				leftBal = leftBal.subtract(detail.getPrincipalBal());
			}
			break;
		}
		case MRF: //等本金且剩余靠前类型
		case MRG: {
			if (i == 0) {
				detail.setPrincipalBal(
						totalBal.subtract(totalBal.divide(BigDecimal.valueOf(totalPeriod), 2, RoundingMode.HALF_UP)
								.multiply(BigDecimal.valueOf(totalPeriod - 1))));
				leftBal = leftBal.subtract(detail.getPrincipalBal());
			} else {
				detail.setPrincipalBal(totalBal.divide(BigDecimal.valueOf(totalPeriod), 2, RoundingMode.HALF_UP));
				leftBal = leftBal.subtract(detail.getPrincipalBal());
			}
			break;
		}
		case MSV: {//剩余靠后类型
			if (i == totalPeriod - 1) {
				detail.setPrincipalBal(leftBal);
				leftBal = BigDecimal.ZERO;
			} else {
				detail.setPrincipalBal(
						getMSVRepayAmt(totalBal, calcRates.get(0).rate.multiply(BigDecimal.valueOf(mult)), totalPeriod)
								.setScale(2, RoundingMode.HALF_UP).subtract(detail.getInterestAmt()));
				leftBal = leftBal.subtract(detail.getPrincipalBal());
			}
			break;
		}
		case MSB: //剩余靠前类型
		case MSF: {
			if (i == 0) {
				detail.setPrincipalBal(
						getMSVRepayAmt(totalBal, calcRates.get(0).rate.multiply(BigDecimal.valueOf(mult)), totalPeriod)
								.setScale(2, RoundingMode.HALF_UP).subtract(detail.getInterestAmt()));
				leftBal = leftBal.subtract(detail.getPrincipalBal());
			} else {
				detail.setPrincipalBal(leftBal);
				leftBal = leftBal.subtract(detail.getPrincipalBal());
			}
			break;
		}
		case OPT: {
			detail.setPrincipalBal(totalBal);
			break;
		}
		case IFP: 
		case IIF: {
			if (i == totalPeriod - 1) {
				detail.setPrincipalBal(leftBal);
				leftBal = BigDecimal.ZERO;
			} else {
				detail.setPrincipalBal(BigDecimal.ZERO);
			}
			break;
		}
		case IWP: {
			detail.setPrincipalBal(leftBal);
			leftBal = BigDecimal.ZERO;
			break;
		}
		default:
			throw new IllegalArgumentException(paymentMethod + "暂不支持");
		}

		tempPaymentPlanDetailExt.setPaymentPlanDetail(detail);
		tempPaymentPlanDetailExt.setLeftBal(leftBal);
		return tempPaymentPlanDetailExt;
	}

	/**
	 * 等额本息第n个月应还本金的计算公式：B=a*i(1+i)^(n-1)/[(1+i)^N-1],a：贷款本金 ，i：贷款月利率， n：贷款月数，
	 * N：还贷总月数
	 * 
	 * @param totalAmt
	 *            贷款本金
	 * @param mRate
	 *            月利率
	 * @param mths
	 *            还款月数
	 * @param nPeriod
	 *            还贷总月数
	 * @return
	 */
	private BigDecimal getMSVRepayAmt(BigDecimal totalAmt, BigDecimal mRate, int mths, int nPeriod) {
		BigDecimal amt = (totalAmt.multiply(mRate).multiply((BigDecimal.ONE.add(mRate)).pow(mths - 1)))
				.divide((BigDecimal.ONE.add(mRate)).pow(nPeriod).subtract(BigDecimal.ONE), 10, RoundingMode.HALF_UP);
		return amt;
	}

	/**
	 * 等额本息计算公式：B=a*i(1+i)^(n-1)/[(1+i)^N-1],a：贷款本金 ，i：贷款月利率， n：贷款月数
	 * 
	 * @param totalAmt
	 *            贷款本金
	 * @param mRate
	 *            月利率
	 * @param mths
	 *            还款月数
	 * @return
	 */
	private BigDecimal getMSVRepayAmt(BigDecimal totalAmt, BigDecimal mRate, int mths) {
		BigDecimal amt = getMSVRepayAmt(totalAmt, mRate, mths, mths);
		return amt;
	}

	/**
	 * 等额本息第n个月还贷利息计算公式：X=BX-B= a*i(1+i)^N/[(1+i)^N-1]-
	 * a*i(1+i)^(n-1)/[(1+i)^N-1], （注：BX=等额本息还贷每月所还本金和利息总额， B=等额本息还贷每月所还本金，
	 * a=贷款总金额 i=贷款月利率， N=还贷总月数， n=第n期还贷数 X=等额本息还贷每月所还的利息）
	 * 
	 * @param totalAmt
	 *            贷款总金额
	 * @param mRate
	 *            贷款月利率
	 * @param mths
	 *            还贷总月数
	 * @param nPeriod
	 *            第n期还贷数
	 * @return
	 */
	private BigDecimal getMSVInterest(BigDecimal totalAmt, BigDecimal mRate, int mths, int nPeriod) {
		BigDecimal amt = (totalAmt.multiply(mRate).multiply((new BigDecimal(1).add(mRate)).pow(mths)))
				.divide((new BigDecimal(1).add(mRate)).pow(mths).subtract(new BigDecimal(1)), 6, RoundingMode.HALF_UP)
				.subtract((totalAmt.multiply(mRate).multiply((new BigDecimal(1).add(mRate)).pow(nPeriod - 1))).divide(
						(new BigDecimal(1).add(mRate)).pow(mths).subtract(new BigDecimal(1)), 6, RoundingMode.HALF_UP));
		return amt;
	}
	
	/**
	 * 用于临时保存循环计算时的对象值
	 * @author luxue
	 *
	 */
	public class TempPaymentPlanDetailExt{
		PaymentPlanDetail paymentPlanDetail;
		/**
		 * 剩余本金
		 */
		BigDecimal leftBal;
		/**
		 * @return the paymentPlanDetail
		 */
		public PaymentPlanDetail getPaymentPlanDetail() {
			return paymentPlanDetail;
		}
		/**
		 * @param paymentPlanDetail the paymentPlanDetail to set
		 */
		public void setPaymentPlanDetail(PaymentPlanDetail paymentPlanDetail) {
			this.paymentPlanDetail = paymentPlanDetail;
		}
		/**
		 * @return the leftBal
		 */
		public BigDecimal getLeftBal() {
			return leftBal;
		}
		/**
		 * @param leftBal the leftBal to set
		 */
		public void setLeftBal(BigDecimal leftBal) {
			this.leftBal = leftBal;
		}
		
	}
}
