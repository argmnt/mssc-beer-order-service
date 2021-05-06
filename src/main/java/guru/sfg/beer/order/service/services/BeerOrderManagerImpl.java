package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.brewery.model.BeerOrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BeerOrderManagerImpl implements BeerOrderManager {

    public static final String ORDER_ID_HEADER = "ORDER_ID_HEADER";

    private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineFactory;

    private final BeerOrderRepository beerOrderRepository;

    @Override
    public BeerOrder newBeerOrder(BeerOrder beerOrder) {
        beerOrder.setId(null);
        beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);

        BeerOrder savedBeerOrder = beerOrderRepository.save(beerOrder);
        sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATE_ORDER);
        return savedBeerOrder;
    }

    @Override
    public void processValidationResult(UUID beerOrderId, Boolean isValid) {
        Optional<BeerOrder> optionalBeerOrder = beerOrderRepository.findById(beerOrderId);
        BeerOrder beerOrder = optionalBeerOrder.orElseThrow(RuntimeException::new);

        if (isValid) {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATION_PASSED);
            //Note: above the state machine automatically update new state of beerOrder with new state via interceptor
            //that's why we need to get new version from db.
            Optional<BeerOrder> optionalUpdatedBeerOrder = beerOrderRepository.findById(beerOrderId);
            BeerOrder updatedBeerOrder = optionalBeerOrder.orElseThrow(RuntimeException::new);
            sendBeerOrderEvent(updatedBeerOrder, BeerOrderEventEnum.ALLOCATE_ORDER);
        } else {
            sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.VALIDATION_FAILED);
        }
    }

    @Override
    public void beerOrderAllocationPassed(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());
        BeerOrder beerOrder = beerOrderOptional.orElseThrow(RuntimeException::new);
        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_SUCCESS);
        updateAllocatedQty(beerOrderDto, beerOrder);
    }

    @Override
    public void beerOrderAllocationPendingInventory(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());
        BeerOrder beerOrder = beerOrderOptional.orElseThrow(RuntimeException::new);
        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_NO_INVENTORY);
        updateAllocatedQty(beerOrderDto, beerOrder);
    }

    @Override
    public void beerOrderAllocationFailed(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());
        BeerOrder beerOrder = beerOrderOptional.orElseThrow(RuntimeException::new);
        sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.ALLOCATION_FAILED);
        updateAllocatedQty(beerOrderDto, beerOrder);
    }

    private void updateAllocatedQty(BeerOrderDto beerOrderDto, BeerOrder beerOrder) {
        Optional<BeerOrder> allocatedOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        BeerOrder allocatedOrder = allocatedOrderOptional.orElseThrow(RuntimeException::new);
        allocatedOrder.getBeerOrderLines().forEach(beerOrderLine -> {
            beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
                if (beerOrderLine.getBeerId().equals(beerOrderLineDto.getBeerId())) {
                    beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
                }
            });
        });

        beerOrderRepository.saveAndFlush(beerOrder);
    }

    private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum beerOrderEventEnum) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = build(beerOrder);
        Message msg = MessageBuilder.withPayload(beerOrderEventEnum)
                .setHeader(ORDER_ID_HEADER, beerOrder.getId().toString())
                .build();
        sm.sendEvent(msg);
    }

    private StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> build(BeerOrder beerOrder) {
        StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = stateMachineFactory.getStateMachine(beerOrder.getId());
        sm.stop();

        sm.getStateMachineAccessor().doWithAllRegions(sma -> {
            sma.resetStateMachine(new DefaultStateMachineContext<>(beerOrder.getOrderStatus(),
                    null, null, null));
        });
        sm.start();
        return sm;
    }
}
