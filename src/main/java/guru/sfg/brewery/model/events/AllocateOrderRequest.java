package guru.sfg.brewery.model.events;

import guru.sfg.brewery.model.BeerOrderDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Builder;
import org.springframework.stereotype.Component;

@Data
@Component
@RequiredArgsConstructor
@Builder
public class AllocateOrderRequest {

    private final BeerOrderDto beerOrderDto;
}
