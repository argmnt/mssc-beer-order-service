package guru.sfg.beer.order.service.web.mappers;

import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.brewery.model.CustomerDto;
import org.mapstruct.Mapper;

@Mapper(uses = {DateMapper.class})
public interface CustomerMapper {

    public CustomerDto customerToDto(Customer customer);

    public Customer dtoToCustomer(CustomerDto dto);
}
